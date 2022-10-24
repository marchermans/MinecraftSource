package buildSrc.utils.decompile;

import org.jetbrains.java.decompiler.main.decompiler.SingleFileSaver;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class LineMappingResultSaver extends SingleFileSaver {
    private static final Logger LOGGER = LoggerFactory.getLogger(LineMappingResultSaver.class);

    private final String source;
    final FernFlowerByteCodeProvider bytecodeProvider;

    public LineMappingResultSaver(final String source, final File target, final FernFlowerByteCodeProvider bytecodeProvider) {
        super(target);
        this.source = source;
        this.bytecodeProvider = bytecodeProvider;
    }

    @Override
    public void saveClassEntry(
            final String path, final String archiveName, final String qualifiedName, final String entryName, final String content, final int[] mapping
    ) {
        super.saveClassEntry(path, archiveName, qualifiedName, entryName, content, mapping);
        if (mapping != null) {
            // Get the input archive name
            try {
                final String inputName = qualifiedName + ".class";
                final byte[] clazz = this.bytecodeProvider.getBytecode(this.source, inputName);
                final ClassReader reader = new ClassReader(clazz);
                final ClassWriter output = new ClassWriter(reader, 0);
                reader.accept(new LineMappingVisitor(output, mapping), 0);
                // Find the entry, then modify it? if possible?
                this.bytecodeProvider.setBytecode(this.source, inputName, output.toByteArray());
            } catch (final IOException ex) {
                LineMappingResultSaver.LOGGER.warn("Line mapping failed on {} in {}", entryName, archiveName, ex);
            }
        }
    }
}
