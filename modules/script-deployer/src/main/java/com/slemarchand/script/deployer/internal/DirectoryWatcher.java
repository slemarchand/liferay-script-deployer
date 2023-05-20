package com.slemarchand.script.deployer.internal;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class DirectoryWatcher extends Thread {

    private final File _directory;
    private AtomicBoolean stop = new AtomicBoolean(false);

    public DirectoryWatcher(File directory) {
        this._directory = directory;
    }
    
    public void start() {
    	setDaemon(true);
    	setPriority(MIN_PRIORITY);
    	super.start();
    }

    public boolean isStopped() { return stop.get(); }
    public void stopThread() { stop.set(true); }

    public abstract void doOnChange(File file);

    @Override
    public void run() {
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            Path path = _directory.toPath();
            path.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
            while (!isStopped()) {
                WatchKey key;
                try { key = watcher.poll(25, TimeUnit.MILLISECONDS); }
                catch (InterruptedException e) { return; }
                if (key == null) { Thread.yield(); continue; }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        Thread.yield();
                        continue;
                    } else if (kind == java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
                            || kind == java.nio.file.StandardWatchEventKinds.ENTRY_CREATE) {
                        doOnChange(new File(_directory,  filename.toString()));
                    }
                    boolean valid = key.reset();
                    if (!valid) { break; }
                }
                Thread.yield();
            }
        } catch (Throwable e) {
            _log.error(e);
        }
    }
    

	private final static Log _log = LogFactoryUtil.getLog(DirectoryWatcher.class);
}
