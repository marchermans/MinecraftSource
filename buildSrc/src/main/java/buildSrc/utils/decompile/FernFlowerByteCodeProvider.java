package buildSrc.utils.decompile;

import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class FernFlowerByteCodeProvider implements IBytecodeProvider, AutoCloseable {

    private final ConcurrentMap<String, FileSystem> files = new ConcurrentHashMap<>();

    public void setBytecode(final String external, final String internal, final byte[] bytes) throws IOException {
        final Path result = this.zipFile(external).getPath(internal);
        Files.write(result, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    @Override
    public void close() throws IOException {
        IOException error = null;
        for (final FileSystem file : this.files.values()) {
            try {
                file.close();
            } catch (final IOException ex) {
                if (error != null) {
                    error.addSuppressed(ex);
                } else {
                    error = ex;
                }
            }
        }
        this.files.clear();
        if (error != null) {
            throw error;
        }
    }

    @Override
    public byte[] getBytecode(final String external, final String internal) throws IOException {
        final Path result = this.zipFile(external).getPath(internal);
        return Files.readAllBytes(result);
    }

    private FileSystem zipFile(final String external) throws IOException {
        try {
            return this.files.computeIfAbsent(external, path -> {
                final URI uri = URI.create("jar:" + new File(path).toURI());
                try {
                    return FileSystems.getFileSystem(uri);
                } catch (final FileSystemNotFoundException ex) {
                    try {
                        return FileSystems.newFileSystem(uri, Collections.emptyMap(), (ClassLoader) null);
                    } catch (final IOException ex2) {
                        throw new RuntimeException(ex2);
                    }
                }
            });
        } catch (final RuntimeException ex) {
            if (ex.getCause() instanceof IOException) {
                throw (IOException) ex.getCause();
            } else {
                throw ex;
            }
        }
    }
}
