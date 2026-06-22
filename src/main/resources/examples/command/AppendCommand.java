package com.javapatterns.examples.command;

/**
 * Concrete command — wraps an append operation on a {@link TextEditor}
 * and knows how to undo itself (remove the last N characters it added).
 */
public final class AppendCommand implements Command {

    private final TextEditor editor;
    private final String textToAppend;

    public AppendCommand(TextEditor editor, String textToAppend) {
        this.editor = editor;
        this.textToAppend = textToAppend;
    }

    @Override
    public String execute() {
        editor.append(textToAppend);
        return "appended '" + textToAppend + "'";
    }

    @Override
    public String undo() {
        editor.removeLast(textToAppend.length());
        return "undid append '" + textToAppend + "'";
    }
}
