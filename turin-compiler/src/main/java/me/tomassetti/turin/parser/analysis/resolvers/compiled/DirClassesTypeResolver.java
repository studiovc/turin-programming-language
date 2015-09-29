package me.tomassetti.turin.parser.analysis.resolvers.compiled;

import me.tomassetti.turin.parser.ast.FunctionDefinition;

import java.io.File;
import java.io.IOException;

/**
 * Resolve types by looking in a dir of class files.
 */
public class DirClassesTypeResolver extends AbstractCompiledTypeResolver<DirClassesClasspathElement> {

    private File dir;

    /**
     * Note that it adds itself in the global ClassPool.
     */
    public DirClassesTypeResolver(File dir) throws IOException {
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("Not existing or not a directory: " + dir.getPath());
        }
        this.dir = dir;
        explore(dir);
        ClassPoolFactory.INSTANCE.addClassesDir(new CompiledClassPath());
    }

    private void explore(File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                explore(child);
            }
        } else if (file.isFile()) {
            if (file.getName().endsWith(".class")) {
                if (file.getName().startsWith(FunctionDefinition.CLASS_PREFIX)) {
                    String name = classFileToFunctionName(file, dir);
                    functionElements.put(name, new DirClassesClasspathElement(file, name));
                } else {
                    String name = classFileToClassName(file, dir);
                    classpathElements.put(name, new DirClassesClasspathElement(file, name));
                }
            }
        }
    }

}