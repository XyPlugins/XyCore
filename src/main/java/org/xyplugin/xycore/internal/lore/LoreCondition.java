package org.xyplugin.xycore.internal.lore;

/** A PlaceholderAPI-backed condition used by a lore command rule. */
final class LoreCondition {

    final String id;
    final String placeholder;
    final String operator;
    final String expected;
    final String denyMessage;

    LoreCondition(String id, String placeholder, String operator, String expected, String denyMessage) {
        this.id = id;
        this.placeholder = placeholder == null ? "" : placeholder;
        this.operator = operator == null ? ">=" : operator.trim();
        this.expected = expected == null ? "" : expected;
        this.denyMessage = denyMessage == null ? "" : denyMessage;
    }
}
