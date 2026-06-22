package com.javapatterns.mcp.detect;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Recognises the Observer pattern (subject side).
 *
 * <p>Heuristic: a class with at least 2 of the following 3 methods:
 * {@code subscribe/register/addListener/addObserver},
 * {@code unsubscribe/remove*},
 * a publish-like method ({@code publish}, {@code notify*}, {@code fire*},
 * {@code dispatch*}, {@code emit*}). Two methods give 0.66 confidence;
 * all three give 1.0.</p>
 */
public final class ObserverDetector implements PatternDetector {

    private static final Set<String> SUBSCRIBE = Set.of(
        "subscribe", "register", "addlistener", "addobserver", "addsubscriber");
    private static final Set<String> UNSUBSCRIBE = Set.of(
        "unsubscribe", "unregister", "removelistener", "removeobserver", "removesubscriber");
    private static final Set<String> PUBLISH_PREFIX = Set.of(
        "publish", "notify", "fire", "dispatch", "emit");

    @Override
    public Pattern pattern() { return Pattern.OBSERVER; }

    @Override
    public List<DetectedPattern> detect(CompilationUnit unit) {
        List<DetectedPattern> hits = new ArrayList<>();
        unit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            if (cls.isInterface()) return;
            List<String> evidence = new ArrayList<>();
            int signals = 0;

            boolean hasSubscribe = cls.getMethods().stream()
                .anyMatch(m -> SUBSCRIBE.contains(m.getNameAsString().toLowerCase()));
            if (hasSubscribe) { signals++; evidence.add("has a subscribe/register-like method"); }

            boolean hasUnsubscribe = cls.getMethods().stream()
                .anyMatch(m -> UNSUBSCRIBE.contains(m.getNameAsString().toLowerCase()));
            if (hasUnsubscribe) { signals++; evidence.add("has an unsubscribe/unregister-like method"); }

            boolean hasPublish = cls.getMethods().stream()
                .anyMatch(m -> PUBLISH_PREFIX.stream()
                    .anyMatch(pref -> m.getNameAsString().toLowerCase().startsWith(pref)));
            if (hasPublish) { signals++; evidence.add("has a publish/notify/fire-like method"); }

            if (signals >= 2) {
                double confidence = signals / 3.0;
                int line = cls.getBegin().map(p -> p.line).orElse(-1);
                hits.add(new DetectedPattern(Pattern.OBSERVER, cls.getNameAsString(), line, confidence, evidence));
            }
        });
        return hits;
    }
}
