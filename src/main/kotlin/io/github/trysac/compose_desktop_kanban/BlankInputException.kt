package io.github.trysac.compose_desktop_kanban

/** Thrown when a text input is empty or contains only whitespace. */
public class BlankInputException : IllegalArgumentException("input is blank")
