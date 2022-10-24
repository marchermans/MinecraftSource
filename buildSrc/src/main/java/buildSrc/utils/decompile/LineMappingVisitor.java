package buildSrc.utils.decompile;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class LineMappingVisitor extends ClassVisitor {
    private final NavigableMap<Integer, Integer> lineMapping = new TreeMap<>();

    public LineMappingVisitor(final ClassVisitor parent, final int[] mapping) {
        super(Opcodes.ASM9, parent); // todo: common version constant
        for (int i = 0; i < mapping.length; i += 2) {
            this.lineMapping.put(mapping[i], mapping[i + 1]);
        }
    }

    @Override
    public MethodVisitor visitMethod(
            final int access, final String name, final String descriptor, final String signature, final String[] exceptions
    ) {
        return new MethodLineFixer(super.visitMethod(access, name, descriptor, signature, exceptions), this.lineMapping);
    }

    static class MethodLineFixer extends MethodVisitor {
        private final NavigableMap<Integer, Integer> lineMapping;
        MethodLineFixer(final MethodVisitor parent, final NavigableMap<Integer, Integer> lineMapping) {
            super(Opcodes.ASM9, parent);
            this.lineMapping = lineMapping;
        }

        @Override
        public void visitLineNumber(final int line, final Label start) {
            Integer mapped = this.lineMapping.get(line);
            if (mapped == null) {
                final Map.Entry<Integer, Integer> entry = this.lineMapping.higherEntry(line);
                if (entry != null) {
                    mapped = entry.getValue();
                }
            }
            super.visitLineNumber(mapped != null ? mapped : line, start);
        }
    }
}
