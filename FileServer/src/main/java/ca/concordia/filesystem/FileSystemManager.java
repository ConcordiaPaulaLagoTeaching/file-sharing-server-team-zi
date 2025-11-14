package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;
import ca.concordia.filesystem.datastructures.FNode;

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
    private FNode[] fnodeTable; // Array of fnodes

    //TO-DO: CHANGE THE FUNCTIONS ACCORDING TO FNode
    public FileSystemManager(String filename, int totalSize) {
        // Initialize the file system manager with a file
        if (instance == null) {
            try {
                this.disk = new RandomAccessFile(filename, "rw");
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }

            inodeTable = new FEntry[MAXFILES];
            fnodeTable = new FNode[MAXBLOCKS];

            for (int i = 0; i < MAXBLOCKS; i++) {
                fnodeTable[i] = new FNode(i);
            }

            freeBlockList = new boolean[MAXBLOCKS];
            for (int i = 0; i < MAXBLOCKS; i++) {
                freeBlockList[i] = true;
            }

            instance = this;
        } else {
            throw new IllegalStateException("FileSystemManager is already initialized.");
        }

    }

    //createFile method (Ileass)
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

    //listFiles method (Ileass)
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

    //writeFile method (Zineb + Ileass)
    public void writeFile(String fileName, byte[] content) {
        globalLock.lock();
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

            if (entry.getFirstBlock() != -1) {
                freeFileBlocks(entry.getFirstBlock());
            }

            int numOfBlocksNeeded = (int) Math.ceil((double) content.length / BLOCK_SIZE);

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

            for (int i = 0; i < numOfBlocksNeeded; i++) {
                int diskIndex = allocatedBlocks[i];

                disk.seek((long) diskIndex * BLOCK_SIZE); //move the disk pointer to the right position
                int startIndex = i * BLOCK_SIZE; //where to start writing in the block
                int endIndex = Math.min(startIndex + BLOCK_SIZE, content.length); //where to stop writing in the block
                disk.write(content, startIndex, endIndex - startIndex); //endIndex - StartIndex = how many byte written per block

                freeBlockList[allocatedBlocks[i]] = false; //after writing in it mark it as used

                if (i < numOfBlocksNeeded - 1) {
                    setFNodeNext(fnodeTable[diskIndex], allocatedBlocks[i + 1]);
                } else {
                    setFNodeNext(fnodeTable[diskIndex], -1); //last block points to -1
                }
            }

            entry.setFilesize((short) content.length);
            entry.setFirstBlock((short) allocatedBlocks[0]);

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            globalLock.unlock();
        }
    }

    //deleteFile method (Zineb + Ileass)
    public void deleteFile(String fileName) {
        globalLock.lock();
        try {
            for (int i = 0; i < inodeTable.length; i++) {
                FEntry entry = inodeTable[i];
                if (entry != null && entry.getFilename().equals(fileName)) {

                    if (entry.getFirstBlock() != -1) {
                        freeFileBlocks(entry.getFirstBlock());

                        int currentBlock = entry.getFirstBlock();
                        byte[] zeros = new byte[BLOCK_SIZE];
                        while (currentBlock != -1) {
                            disk.seek((long) currentBlock * BLOCK_SIZE);
                            disk.write(zeros);
                            currentBlock = getFNodeNext(fnodeTable[currentBlock]);
                        }
                    }

                    inodeTable[i] = null; //mark it as delete
                    return;
                }
            }
            throw new IllegalArgumentException(fileName + " does not exist");
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            globalLock.unlock();
        }
    }

    //readFile method (Zineb)
    public byte[] readFile(String fileName) {
        globalLock.lock();
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
            int fileSizeRead = entry.getFilesize();
            short firstBlockRead = entry.getFirstBlock();

            if (fileSizeRead == 0 || firstBlockRead == -1) {
                return new byte[0]; //empty file
            }

            byte[] fileData = new byte[fileSizeRead];
            int bytesRead = 0;
            int currentBlock = firstBlockRead;

            while (currentBlock != -1 && bytesRead < fileSizeRead) {
                disk.seek((long) currentBlock * BLOCK_SIZE);
                int bytesToRead = Math.min(BLOCK_SIZE, fileSizeRead - bytesRead);
                disk.readFully(fileData, bytesRead, bytesToRead);
                bytesRead += bytesToRead;

                currentBlock = getFNodeNext(fnodeTable[currentBlock]);
            }

            return fileData;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            globalLock.unlock();
        }
    }

    private void freeFileBlocks(int firstBlock) {
        int currentBlock = firstBlock;
        while (currentBlock != -1) {
            int nextBlock = getFNodeNext(fnodeTable[currentBlock]);
            freeBlockList[currentBlock] = true; //mark block as free
            setFNodeNext(fnodeTable[currentBlock], -1);
            currentBlock = nextBlock;
        }
    }

    private int getFNodeNext(FNode node) {
        try {
            java.lang.reflect.Field nextField = FNode.class.getDeclaredField("next");
            nextField.setAccessible(true);
            return (int) nextField.get(node);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setFNodeNext(FNode node, int next) {
        try {
            java.lang.reflect.Field nextField = FNode.class.getDeclaredField("next");
            nextField.setAccessible(true);
            nextField.set(node, next);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}