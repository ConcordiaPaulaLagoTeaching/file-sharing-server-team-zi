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


    // TODO: Add readFile, writeFile and other required methods,
}
