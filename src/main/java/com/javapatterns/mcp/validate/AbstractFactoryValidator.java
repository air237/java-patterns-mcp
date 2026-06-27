package com.javapatterns.mcp.validate;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.javapatterns.mcp.catalog.Pattern;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates {@link Pattern#ABSTRACT_FACTORY} implementations.
 *
 * <p>Shape filter (mirrors {@code AbstractFactoryDetector}): a type
 * with ≥2 abstract create / make / new / build methods whose return
 * types are all DIFFERENT (a product family), with at least one
 * concrete implementor in the same file.</p>
 *
 * <p>Rules:</p>
 * <ul>
 *   <li><b>ERROR</b> — the factory contract is a concrete class. The
 *       whole point of Abstract Factory is letting clients swap one
 *       factory implementation for another; a concrete root locks
 *       callers to a single variant.</li>
 *   <li><b>WARNING</b> — a concrete factory method returns a
 *       concrete product type instead of the abstract product
 *       (interface or abstract class). Returning a concrete type
 *       leaks the variant and breaks substitutability — clients see
 *       {@code HtmlButton}, not {@code Button}.</li>
 *   <li><b>INFO</b> — only one concrete factory is visible in the
 *       file. A single-variant Abstract Factory is over-engineering;
 *       either add a second variant or drop the pattern.</li>
 * </ul>
 */
public final class AbstractFactoryValidator implements PatternValidator {

    private static final List<String> CREATE_PREFIXES =
        List.of("create", "make", "new", "build");

    @Override
    public Pattern pattern() { return Pattern.ABSTRACT_FACTORY; }

    @Override
    public List<ValidationIssue> validate(CompilationUnit unit) {
        List<ValidationIssue> issues = new ArrayList<>();
        List<ClassOrInterfaceDeclaration> all = unit.findAll(ClassOrInterfaceDeclaration.class);

        for (ClassOrInterfaceDeclaration cand : all) {
            String name = cand.getNameAsString();
            int line = cand.getBegin().map(p -> p.line).orElse(-1);

            // Collect "product creation" methods on this type.
            Set<String> productTypes = new HashSet<>();
            List<MethodDeclaration> productMethods = new ArrayList<>();
            for (MethodDeclaration m : cand.getMethods()) {
                String n = m.getNameAsString();
                if (CREATE_PREFIXES.stream().noneMatch(n::startsWith)) continue;
                if (m.getType().isVoidType() || m.getType().isPrimitiveType()) continue;
                productTypes.add(m.getType().toString());
                productMethods.add(m);
            }
            // Abstract Factory needs ≥2 differently-typed product methods.
            if (productTypes.size() < 2) continue;

            // Find concrete implementors in the file.
            List<ClassOrInterfaceDeclaration> concreteFactories = new ArrayList<>();
            for (ClassOrInterfaceDeclaration s : all) {
                if (s == cand) continue;
                if (s.isInterface() || s.isAbstract()) continue;
                boolean impl = s.getImplementedTypes().stream()
                    .anyMatch(t -> t.getNameAsString().equals(name))
                    || s.getExtendedTypes().stream()
                    .anyMatch(t -> t.getNameAsString().equals(name));
                if (impl) concreteFactories.add(s);
            }

            // ─── ERROR: factory contract is concrete ───────────────
            boolean isAbstraction = cand.isInterface() || cand.isAbstract();
            if (!isAbstraction) {
                // Only flag if there ARE concrete implementors / subclasses; a
                // standalone concrete "factory" without implementors is just a
                // helper class, not an Abstract-Factory anti-pattern.
                if (!concreteFactories.isEmpty()) {
                    issues.add(new ValidationIssue(
                        Pattern.ABSTRACT_FACTORY, name, line, Severity.ERROR,
                        "Abstract Factory contract " + name + " is a concrete class — " +
                        "clients are locked to a single variant and cannot substitute alternatives.",
                        "Convert " + name + " to an interface (or abstract class) so " +
                        "alternative factories can be substituted polymorphically."
                    ));
                }
                continue; // remaining rules need an abstraction
            }

            // ─── WARNING: concrete factory returns concrete product types ─
            // Build the set of all class/interface names declared in the file
            // so we can ask "is this return type a concrete class?".
            Set<String> concreteTypesInFile = new HashSet<>();
            for (ClassOrInterfaceDeclaration t : all) {
                if (!t.isInterface() && !t.isAbstract()) {
                    concreteTypesInFile.add(t.getNameAsString());
                }
            }
            for (ClassOrInterfaceDeclaration cf : concreteFactories) {
                for (MethodDeclaration m : cf.getMethods()) {
                    String n = m.getNameAsString();
                    if (CREATE_PREFIXES.stream().noneMatch(n::startsWith)) continue;
                    if (!(m.getType() instanceof ClassOrInterfaceType rt)) continue;
                    String rtName = rt.getNameAsString();
                    if (!concreteTypesInFile.contains(rtName)) continue;
                    int mLine = m.getBegin().map(p -> p.line).orElse(line);
                    issues.add(new ValidationIssue(
                        Pattern.ABSTRACT_FACTORY, cf.getNameAsString(), mLine, Severity.WARNING,
                        "Concrete factory " + cf.getNameAsString() + "." + n +
                        "() returns concrete product type " + rtName +
                        " — clients see the variant, breaking substitutability.",
                        "Return the abstract product type (interface or abstract class) " +
                        "instead of " + rtName + "."
                    ));
                }
            }

            // ─── INFO: only one variant present ────────────────────
            if (concreteFactories.size() == 1) {
                issues.add(new ValidationIssue(
                    Pattern.ABSTRACT_FACTORY, name, line, Severity.INFO,
                    "Only one concrete factory (" + concreteFactories.get(0).getNameAsString() +
                    ") implements " + name + ". A single-variant Abstract Factory is " +
                    "over-engineering — the abstraction earns its keep with ≥2 variants.",
                    "Either add a second concrete factory variant (the canonical reason for the " +
                    "pattern), or drop the factory abstraction and instantiate products directly."
                ));
            }
        }

        return issues;
    }
}
