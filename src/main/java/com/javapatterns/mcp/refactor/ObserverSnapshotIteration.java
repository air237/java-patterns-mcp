package com.javapatterns.mcp.refactor;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Inside a publish-like method of an Observer subject, wrap the
 * iterated collection expression with {@code List.copyOf(...)} so that
 * a listener that subscribes / unsubscribes during dispatch does not
 * trigger ConcurrentModificationException.
 *
 * <p>Idempotency: if the iterated expression already contains
 * {@code copyOf}, the foreach is left alone.</p>
 */
public final class ObserverSnapshotIteration implements PatternRefactoring {

    private static final Set<String> PUBLISH_PREFIX = Set.of(
        "publish", "notify", "fire", "dispatch", "emit");

    @Override
    public RefactoringId id() { return RefactoringId.OBSERVER_SNAPSHOT_ITERATION; }

    @Override
    public RefactoringResult apply(CompilationUnit unit) {
        List<String> changes = new ArrayList<>();

        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            if (cls.isInterface()) return;

            for (MethodDeclaration m : cls.getMethods()) {
                String name = m.getNameAsString().toLowerCase();
                boolean isPublishLike = PUBLISH_PREFIX.stream().anyMatch(name::startsWith);
                if (!isPublishLike) continue;

                m.findAll(ForEachStmt.class).forEach(stmt -> {
                    Expression iter = stmt.getIterable();
                    String iterStr = iter.toString();
                    // Already wrapped? skip.
                    if (iterStr.contains("copyOf") || iterStr.contains("new ArrayList")) return;
                    // Only rewrap simple name references; complex expressions stay manual.
                    if (!(iter instanceof NameExpr)) return;

                    Expression replacement = StaticJavaParser.parseExpression(
                        "java.util.List.copyOf(" + iterStr + ")");
                    stmt.setIterable(replacement);
                    int line = stmt.getBegin().map(p -> p.line).orElse(-1);
                    changes.add(cls.getNameAsString() + "." + m.getNameAsString() +
                        "(): wrapped iterable `" + iterStr + "` with List.copyOf(...) at line " + line);
                });
            }
        });

        String newSource = LexicalPreservingPrinter.print(unit);
        return new RefactoringResult(id(), newSource, !changes.isEmpty(), changes);
    }
}
