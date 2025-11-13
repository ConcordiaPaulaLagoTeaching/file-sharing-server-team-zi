package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;

import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.util.concurrent.locks.ReentrantLock;

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private static FileSystemManager instance;
    private final RandomAccessFile disk;
    private final ReentrantLock globalLock = new ReentrantLock();

    private static final int BLOCK_SIZE = 128; // Example block size

    private FEntry[] inodeTable; // Array of inodes
    private boolean[] freeBlockList; // Bitmap for free blocks
    //TO-DO: CHANGE THE FUNCTIONS ACCORDING TO FNode
    public FileSystemManager(String filename, int totalSize) {
        // Initialize the file system manager with a file
        if(instance == null) {
            try {
                this.disk = new RandomAccessFile(filename, "rw");
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }

            inodeTable = new FEntry[MAXFILES];

            freeBlockList = new boolean[MAXBLOCKS];
            for(int i = 0; i < MAXBLOCKS; i++){
                freeBlockList[i] = true;
            }

            instance = this;
        } else {
            throw new IllegalStateException("FileSystemManager is already initialized.");
        }

    }

    public void createFile(String fileName) {
        globalLock.lock();
        try {
            for (FEntry entry : inodeTable) {
                if (entry != null && entry.getFilename().equals(fileName)) {
                    throw new IllegalArgumentException("File already exists.");
                }
            }

            int freeInode = -1;
            for (int i = 0; i < inodeTable.length; i++) {
                if (inodeTable[i] == null) {
                    freeInode = i;
                    break;
                }
            }
            if (freeInode == -1) {
                throw new IllegalStateException("No free inodes available.");
            }

            inodeTable[freeInode] = new FEntry(fileName, (short) 0, (short) -1);

        } finally {
            globalLock.unlock();
        }
    }

    public String[] listFiles() {
        globalLock.lock();
        try {
            return java.util.Arrays.stream(inodeTable)
                    .filter(entry -> entry != null)
                    .map(FEntry::getFilename)
                    .toArray(String[]::new);
        } finally {
            globalLock.unlock();
        }
    }

    //writeFile method (Zineb)
    public void writeFile(String fileName, byte[] content) {
        globalLock.lock();
        try {
            FEntry entry = null; //initialize the variable that will hold the file we find inside the loop
            for(FEntry e:inodeTable) {
                if(e != null && e.getFilename().equals(fileName)){
                    entry = e;
                    break;
                }
            }
            if(entry == null) {
                throw new IllegalArgumentException(fileName + " does not exist");
            }
            int numOfBlocksNeeded = (int) Math.ceil((double) content.length/BLOCK_SIZE);

            int[] allocatedBlocks = new int[numOfBlocksNeeded];
            int found = 0;
            for(int i = 0; i < freeBlockList.length; i++) {
                if (found < numOfBlocksNeeded) {
                    allocatedBlocks[found++] = i;
                }
                else {
                    break;
                }
            }
            if(found < numOfBlocksNeeded) {
                throw new IllegalStateException("Error, file is too large");
            }

            for(int i = 0; i < numOfBlocksNeeded; i++) {
                int diskIndex = allocatedBlocks[i];
                disk.seek((long)diskIndex * BLOCK_SIZE); //move the disk pointer to the right position
                int startIndex = i*BLOCK_SIZE; //where to start writing in the block
                int endIndex = Math.min(startIndex + BLOCK_SIZE, content.length); //where to stop writing in the block
                disk.write(content, startIndex, endIndex - startIndex); //endIndex - StartIndex = how many byte written per block
                freeBlockList[allocatedBlocks[i]] = false; //after writing in it mark it as used
            }

            entry.setFilesize((short) content.length);
            entry.setFirstBlock((short) allocatedBlocks[0]);
        } catch(Exception e) {
            throw new RuntimeException(e);
        } finally {
            globalLock.unlock();
        }
    }
    //deleteFile method (Zineb)
    public void deleteFile(String fileName) {
        globalLock.lock();
        try {
            for(int i = 0; i < inodeTable.length; i++) {
                FEntry entry = inodeTable[i];
                if(entry != null && entry.getFilename().equals(fileName)) {
                    inodeTable[i] = null; //mark it as delete
                    for(int j = 0; j < freeBlockList.length; j++) {
                        freeBlockList[j] = true;
                    }
                    return;
                }
            }
            throw new IllegalArgumentException(fileName + " does not exist");
        } finally {
            globalLock.unlock();
        }
    }

    public byte[] readFile(String fileName) {
        globalLock.lock();
        try {
            FEntry entry = null;
            for(FEntry e:inodeTable) {
                if(e != null && e.getFilename().equals(fileName)) {
                    entry = e;
                    break;
                }
            }
            if(entry == null) {
                throw new IllegalArgumentException(fileName + " does not exist");
            }
            int fileSizeRead = entry.getFilesize();
            short firstBlockRead = entry.getFirstBlock();

            int numOfBlocksNeeded = (int) Math.ceil((double) fileSizeRead/BLOCK_SIZE);
            byte[] memorySize = new byte[fileSizeRead];//create space in memory
            int bytesRead = 0;
            for(int i = 0; i < numOfBlocksNeeded; i++) {
                int blockIndex = firstBlockRead + i;
                disk.seek((long) blockIndex * BLOCK_SIZE);
                int nextBytesRead = Math.min(BLOCK_SIZE, fileSizeRead - bytesRead);
                disk.readFully(memorySize, bytesRead, nextBytesRead);
                bytesRead += nextBytesRead;
            }
            return memorySize;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            globalLock.unlock();
        }
    }
}


// TODO: Add readFile, writeFile and other required methods,

