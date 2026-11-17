package io.github.trysac.compose_desktop_kanban

/** The input is empty or holds only whitespace; the message is `input is blank`. */
public class BlankInputException : IllegalArgumentException("input is blank")
