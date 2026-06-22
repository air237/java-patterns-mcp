package com.javapatterns.mcp.detect;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.List;

/**
 * Recognises the Singleton pattern.
 *
 * <p>Confidence is built from independent structural signals, each
 * contributing 0.25 of total confidence:</p>
 * <ol>
 *   <li>At least one {@code private} constructor.</li>
 *   <li>A {@code private static} field whose declared type is the
 *       enclosing class itself ({@code private static final Foo INSTANCE = ...}
 *       in {@code Foo}).</li>
 *   <li>A {@code public static} method returning the enclosing class
 *       (the conventional {@code getInstance()}).</li>
 *   <li>A {@code private static} nested class holding the single
 *       instance (Bill-Pugh holder idiom) — strong evidence, not strictly
 *       required.</li>
 * </ol>
 *
 * <p>Three of four signals → confidence ≥ 0.75 (reported);
 * less than two → not reported at all (likely a false positive).</p>
 */
public final class SingletonDetector implements PatternDetector {

    @Override
    public Pattern pattern() { return Pattern.SINGLETON; }

    @Override
    public List<DetectedPattern> detect(CompilationUnit unit) {
        List<DetectedPattern> hits = new ArrayList<>();
        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            if (cls.isInterface()) return;
            String className = cls.getNameAsString();
            List<String> evidence = new ArrayList<>();
            int signals = 0;

            // 1) private constructor(s)
            boolean privateCtor = cls.getConstructors().stream()
                .anyMatch(ConstructorDeclaration::isPrivate);
            if (privateCtor) {
                signals++;
                evidence.add("at least one private constructor");
            }

            // 2) private static field of the same type
            boolean selfTypeField = cls.getFields().stream()
                .filter(FieldDeclaration::isPrivate)
                .filter(FieldDeclaration::isStatic)
                .flatMap(f -> f.getVariables().stream())
                .anyMatch(v -> typeNameOrEmpty(v).equals(className));
            if (selfTypeField) {
                signals++;
                evidence.add("private static field whose declared type is " + className);
            }

            // 3) public static method returning the enclosing class
            boolean getInstanceLike = cls.getMethods().stream()
                .filter(MethodDeclaration::isStatic)
                .filter(MethodDeclaration::isPublic)
                .anyMatch(m -> m.getType().toString().equals(className));
            if (getInstanceLike) {
                signals++;
                evidence.add("public static method returning " + className + " (getInstance pattern)");
            }

            // 4) Bill-Pugh holder idiom — nested private static class
            //    that contains a static field of the outer type.
            boolean holderIdiom = cls.getMembers().stream()
                .filter(m -> m instanceof ClassOrInterfaceDeclaration)
                .map(m -> (ClassOrInterfaceDeclaration) m)
                .filter(inner -> inner.isStatic() && !inner.isInterface())
                .anyMatch(inner -> inner.getFields().stream()
                    .filter(FieldDeclaration::isStatic)
                    .flatMap(f -> f.getVariables().stream())
                    .anyMatch(v -> typeNameOrEmpty(v).equals(className)));
            if (holderIdiom) {
                signals++;
                evidence.add("nested static holder class (Bill-Pugh idiom)");
            }

            if (signals >= 2) {
                double confidence = signals * 0.25;
                int line = cls.getBegin().map(p -> p.line).orElse(-1);
                hits.add(new DetectedPattern(Pattern.SINGLETON, className, line, confidence, evidence));
            }
        });
        return hits;
    }

    private static String typeNameOrEmpty(VariableDeclarator v) {
        return v.getType() instanceof ClassOrInterfaceType cit ? cit.getNameAsString() : "";
    }
}
