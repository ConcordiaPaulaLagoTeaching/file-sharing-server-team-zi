package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;
import ca.concordia.filesystem.datastructures.FNode;

import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.util.concurrent.locks.ReadWriteLock;

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private static FileSystemManager instance;
    private final RandomAccessFile disk;

    private static final int BLOCK_SIZE = 128; // Example block size

    private FEntry[] inodeTable; // Array of inodes
    private boolean[] freeBlockList; // Bitmap for free blocks
    private FNode[] fnodeTable; // Array of fnodes
    private final ReadWriteLock rwLock = new java.util.concurrent.locks.ReentrantReadWriteLock();

    //TO-DO: CHANGE THE FUNCTIONS ACCORDING TO FNode
    public FileSystemManager(String filename, int totalSize) {

        if(instance != null) {
            throw new IllegalStateException("FileSystemManager is already initialized.");
        }

        try {
            this.disk = new RandomAccessFile(filename, "rw");

            if (disk.length() < totalSize) {
                disk.setLength(totalSize);
            }

            inodeTable = new FEntry[MAXFILES];
            fnodeTable = new FNode[MAXBLOCKS];
            freeBlockList = new boolean[MAXBLOCKS];

            freeBlockList[0] = false; // Reserve block 0 for metadata

            for (int i = 0; i < MAXBLOCKS; i++) {
                fnodeTable[i] = new FNode(i);
            }

            if (disk.length() > 0 && existsMetadata()) {
                loadMetadata();
            } else {
                freshFileSystem();
            }

            instance = this;

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize File System.", e);
        }

    }

    //createFile method (Ileass)
    public void createFile(String fileName) throws Exception {
        rwLock.writeLock().lock();
        try {
            for (FEntry entry : inodeTable) {
                if (entry != null && entry.getFilename().equals(fileName)) {
                    throw new IllegalArgumentException("File already exists.");
                }
            }

            int freeInode = -1;
            for (int i = 0; i < MAXFILES; i++) {
                if (inodeTable[i] == null) {
                    freeInode = i;
                    break;
                }
            }
            if (freeInode == -1) {
                throw new IllegalStateException("No free file entries available.");
            }

            inodeTable[freeInode] = new FEntry(fileName, (short) 0, (short) -1);

            saveMetadata();

        } finally {
            rwLock.writeLock().unlock();
        }
    }

    //listFiles method (Ileass)
    public String[] listFiles() {
        rwLock.readLock().lock();
        try {
            return java.util.Arrays.stream(inodeTable)
                    .filter(entry -> entry != null)
                    .map(FEntry::getFilename)
                    .toArray(String[]::new);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    //writeFile method (Zineb + Ileass)
    public void writeFile(String fileName, byte[] content) throws Exception {
        rwLock.writeLock().lock();
        try {
            FEntry entry = null; //initialize the variable that will hold the file we find inside the loop
            for (FEntry e : inodeTable) {
                if (e != null && e.getFilename().equals(fileName)) {
                    entry = e;
                    break;
                }
            }
            if (entry == null) {
                throw new IllegalArgumentException(fileName + " does not exist");
            }

            int numOfBlocksNeeded = (int) Math.ceil((double) content.length / (double) BLOCK_SIZE);

            int[] allocatedBlocks = new int[numOfBlocksNeeded];
            int found = 0;
            for (int i = 0; i < freeBlockList.length; i++) {
                if (freeBlockList[i]) {
                    allocatedBlocks[found] = i;
                    found++;
                    if (found == numOfBlocksNeeded) {
                        break;
                    }
                }
            }
            if (found < numOfBlocksNeeded) {
                throw new IllegalStateException("Error, file is too large");
            }

            if (entry.getFirstBlock() != -1) {
                freeFileBlocks(entry.getFirstBlock());
            }

            for (int i = 0; i < numOfBlocksNeeded; i++) {
                int diskIndex = allocatedBlocks[i];
                int startIndex = i * BLOCK_SIZE; //where to start writing in the block
                int endIndex = Math.min(startIndex + BLOCK_SIZE, content.length); //where to stop writing in the block

                disk.seek((long) diskIndex * BLOCK_SIZE); //move the disk pointer to the right position
                disk.write(content, startIndex, endIndex - startIndex); //endIndex - StartIndex = how many byte written per block

                freeBlockList[diskIndex] = false; //after writing in it mark it as used

                if (i < allocatedBlocks.length - 1) {
                    fnodeTable[diskIndex].setNext(allocatedBlocks[i + 1]);
                } else {
                    fnodeTable[diskIndex].setNext(-1); //last block points to -1
                }
            }

            entry.setFilesize((short) content.length);
            entry.setFirstBlock((short) allocatedBlocks[0]);

            saveMetadata();

        } catch (Exception e) {
            throw e;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    //deleteFile method (Zineb + Ileass)
    public void deleteFile(String fileName) throws Exception {
        rwLock.writeLock().lock();
        try {
            for (int i = 0; i < inodeTable.length; i++) {
                FEntry entry = inodeTable[i];
                if (entry != null && entry.getFilename().equals(fileName)) {

                    if (entry.getFirstBlock() != -1) {
                        freeFileBlocks(entry.getFirstBlock());
                    }

                    inodeTable[i] = null; //mark it as delete

                    saveMetadata();
                    return;
                }
            }
            throw new IllegalArgumentException(fileName + " does not exist");

        } finally {
            rwLock.writeLock().unlock();
        }
    }

    //readFile method (Zineb)
    public byte[] readFile(String fileName) throws Exception{
        rwLock.readLock().lock();
        try {
            FEntry entry = null;
            for (FEntry e : inodeTable) {
                if (e != null && e.getFilename().equals(fileName)) {
                    entry = e;
                    break;
                }
            }
            if (entry == null) {
                throw new IllegalArgumentException(fileName + " does not exist");
            }

            if (entry.getFilesize() == 0) {
                return new byte[0]; //empty file
            }

            byte[] fileData = new byte[entry.getFilesize()];
            int bytesRead = 0;
            int currentBlock = entry.getFirstBlock();

            while (currentBlock != -1 && bytesRead < fileData.length) {
                disk.seek((long) currentBlock * BLOCK_SIZE);
                int bytesToRead = Math.min(BLOCK_SIZE, fileData.length - bytesRead);
                disk.readFully(fileData, bytesRead, bytesToRead);
                bytesRead += bytesToRead;

                currentBlock = fnodeTable[currentBlock].getNext();
            }

            return fileData;

        } finally {
            rwLock.readLock().unlock();
        }
    }

    private void freeFileBlocks(int firstBlock) throws Exception {
        int currentBlock = firstBlock;
        byte[] zeros = new byte[BLOCK_SIZE];

        while (currentBlock != -1) {
            int nextBlock = fnodeTable[currentBlock].getNext();

            disk.seek((long) currentBlock * BLOCK_SIZE);
            disk.write(zeros);

            freeBlockList[currentBlock] = true; //mark block as free
            fnodeTable[currentBlock].setNext(-1);
            currentBlock = nextBlock;
        }
    }

    private void saveMetadata() throws Exception{
        disk.seek(0);

        // Write inode table
        for (FEntry entry : inodeTable) {
            if (entry != null) {
                writeFixedString(entry.getFilename(), 11);
                disk.writeShort(entry.getFilesize());
                disk.writeShort(entry.getFirstBlock());
            } else {
                writeFixedString("", 11);
                disk.writeShort(0);
                disk.writeShort(-1);
            }
        }

        // Write fnode table
        for (FNode node : fnodeTable) {
            disk.writeInt(node.getBlockIndex());
            disk.writeInt(node.getNext());
        }

        // Write free block list
        for (boolean free : freeBlockList) {
            disk.writeBoolean(free);
        }

    }

    private void loadMetadata() throws Exception {
        disk.seek(0);

        for (int i = 0; i < MAXFILES; i++) {
            String name = readFixedString(11);
            short filesize = disk.readShort();
            short firstBlock = disk.readShort();

            if (!name.trim().isEmpty()) {
                inodeTable[i] = new FEntry(name.trim(), filesize, firstBlock);
            }
        }

        for (int i = 0; i < MAXBLOCKS; i++) {
            int blockIndex = disk.readInt();
            int next = disk.readInt();
            fnodeTable[i].setNext(next);

        }

        for (int i = 0; i < MAXBLOCKS; i++) {
            freeBlockList[i] = disk.readBoolean();
        }

    }

    private boolean existsMetadata(){
        try {
            disk.seek(0);
            byte[] header = new byte[16];
            disk.readFully(header);

            for (byte b : header) {
                if (b != 0) {
                    return true;
                }
            }
        } catch (Exception ignored) {}

        return false;
    }

    private void freshFileSystem() throws Exception {
        for (int i = 1; i < MAXBLOCKS; i++) {
            freeBlockList[i] = true; //all blocks are free except block 0
        }

        saveMetadata();
    }

    private void writeFixedString(String s, int length) throws Exception {
        byte[] bytes = s.getBytes();
        byte[] buffer = new byte[length];

        int copyLen = Math.min(bytes.length, length);
        System.arraycopy(bytes, 0, buffer, 0, copyLen);

        disk.write(buffer);
    }

    private String readFixedString(int length) throws Exception {
        byte[] buffer = new byte[length];
        disk.readFully(buffer);

        return new String(buffer).trim();
    }
}