package com.javapatterns.mcp.refactor;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.util.ArrayList;
import java.util.List;

/**
 * Add a private {@code Object readResolve()} that returns
 * {@code getInstance()} to a Serializable Singleton that lacks one.
 *
 * <p>Triggers only when:</p>
 * <ul>
 *   <li>the class {@code implements Serializable},</li>
 *   <li>the class has a {@code getInstance()} method,</li>
 *   <li>the class has no {@code readResolve()} method.</li>
 * </ul>
 */
public final class SingletonAddReadResolve implements PatternRefactoring {

    @Override
    public RefactoringId id() { return RefactoringId.SINGLETON_ADD_READ_RESOLVE; }

    @Override
    public RefactoringResult apply(CompilationUnit unit) {
        List<String> changes = new ArrayList<>();

        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            if (cls.isInterface()) return;

            boolean isSerializable = cls.getImplementedTypes().stream()
                .anyMatch(t -> t.getNameAsString().equals("Serializable"));
            if (!isSerializable) return;

            boolean hasGetInstance = cls.getMethods().stream()
                .filter(MethodDeclaration::isStatic)
                .anyMatch(m -> m.getNameAsString().equals("getInstance"));
            if (!hasGetInstance) return;

            boolean hasReadResolve = cls.getMethods().stream()
                .anyMatch(m -> m.getNameAsString().equals("readResolve"));
            if (hasReadResolve) return; // already done

            MethodDeclaration readResolve = (MethodDeclaration)
                StaticJavaParser.parseBodyDeclaration(
                    "private Object readResolve() {\n" +
                    "    return getInstance();\n" +
                    "}");
            cls.getMembers().add(readResolve);

            int line = cls.getBegin().map(p -> p.line).orElse(-1);
            changes.add(cls.getNameAsString() + ": added readResolve() returning " +
                "getInstance() (line " + line + ")");
        });

        String newSource = LexicalPreservingPrinter.print(unit);
        return new RefactoringResult(id(), newSource, !changes.isEmpty(), changes);
    }
}
