package me.maborg;

import java.io.File;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ConcurrentHierarchicalFolderSizeCalculator {
    private final ConcurrentHashMap<String, FolderInfo> folderMap = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock rootLock = new ReentrantReadWriteLock();
    private volatile String rootPath;
    private AtomicBoolean stop = new AtomicBoolean(false);

    public void startCalculation(String rootPath) {
        stop.set(false);
        this.rootPath = rootPath;
        Thread calculationThread = new Thread(() -> calculateFolderSizes(rootPath));
        calculationThread.start();
    }

    private void calculateFolderSizes(String path) {
        File rootFolder = new File(path);
        if (!rootFolder.isDirectory()) {
            throw new IllegalArgumentException("The provided path is not a directory");
        }

        calculateFolderInfo(rootFolder);
    }

    private void calculateFolderInfo(File rootFolder) {
        Deque<File> folderStack = new LinkedList<>();
        folderStack.push(rootFolder);

        while (!folderStack.isEmpty() && !stop.get()) {
            File currentFolder = folderStack.pop();
            long folderSize = 0;
            ArrayList<String> subfolderPaths = new ArrayList<>();

            File[] files = currentFolder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        folderSize += file.length();
                    } else if (file.isDirectory()) {
                        subfolderPaths.add(file.getAbsolutePath());
                        folderStack.push(file);
                    }
                }
            }

            String currentPath = currentFolder.getAbsolutePath();
            FolderInfo folderInfo = new FolderInfo(this,currentPath, folderSize, subfolderPaths);
            folderMap.put(currentPath, folderInfo);

            // Update parent folder size
            File parentFile = currentFolder.getParentFile();
            if (parentFile != null) {
                String parentPath = parentFile.getAbsolutePath();
                long finalFolderSize = folderSize;
                folderMap.computeIfPresent(parentPath, (k, v) -> {
                    v.addToSize(finalFolderSize);
                    return v;
                });
            }
        }
    }

    public FolderInfo getRootFolderInfo() {
        rootLock.readLock().lock();
        try {
            return folderMap.get(rootPath);
        } finally {
            rootLock.readLock().unlock();
        }
    }

    public FolderInfo getFolderInfo(String path) {
        return folderMap.get(path);
    }

    public void stop() {
        stop.set(true);
    }

    public static class FolderInfo {
        private final ConcurrentHierarchicalFolderSizeCalculator calculator;
        private final String path;
        private final AtomicLong size;
        private final List<String> subfolderPaths;
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

        public FolderInfo(ConcurrentHierarchicalFolderSizeCalculator calculator,
            String path, long size, List<String> subfolderPaths) {
            this.calculator = calculator;
            this.path = path;
            this.size = new AtomicLong(size);
            this.subfolderPaths = subfolderPaths;
        }

        public String getPath() {
            return path;
        }

        public long getSize() {
            return size.get();
        }

        public void addToSize(long delta) {
            size.addAndGet(delta);
        }

        public List<String> getSubfolderPaths() {
            lock.readLock().lock();
            try {
                return new ArrayList<>(subfolderPaths);
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public String toString() {
            return "Path: " + path + ", Size: " + size.get() + " bytes, Subfolders: " + subfolderPaths.size();
        }

        public ConcurrentHierarchicalFolderSizeCalculator getCalculator() {
            return calculator;
        }
    }
}
