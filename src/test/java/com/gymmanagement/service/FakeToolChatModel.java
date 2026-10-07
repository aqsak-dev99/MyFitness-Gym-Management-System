package com.gymmanagement.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * A hand-written, scripted fake of ToolChatModel, used ONLY in tests —
 * same idea as FakeAiChatClient, but for a model that takes several
 * turns. You queue up what the "model" will say next (final text, a tool
 * request, or a failure); each generate() call pops the next item and
 * records exactly what it was sent, so a test can assert on both what
 * came back AND what the orchestration loop handed the model.
 *
 * No network, no API key, no mocking framework.
 */
public class FakeToolChatModel implements ToolChatModel {

    /** One recorded generate() call. */
    public record Call(String systemInstruction, List<Turn> conversation, List<ToolSpec> tools) {}

    private final Deque<Object> script = new ArrayDeque<>();
    private final List<Call>    calls  = new ArrayList<>();

    // ── scripting ─────────────────────────────────────────

    public FakeToolChatModel thenText(String text) {
        script.add(new Turn("model", List.of(Part.ofText(text))));
        return this;
    }

    public FakeToolChatModel thenCall(String toolName, Map<String, Object> args) {
        script.add(new Turn("model", List.of(callPart(toolName, args, null))));
        return this;
    }

    /** Several tool requests in a single model turn (Gemini's "parallel" calls). */
    public FakeToolChatModel thenCalls(List<Part> callParts) {
        script.add(new Turn("model", callParts));
        return this;
    }

    public FakeToolChatModel thenTurn(Turn turn) {
        script.add(turn);
        return this;
    }

    public FakeToolChatModel thenFail(RuntimeException failure) {
        script.add(failure);
        return this;
    }

    public static Part callPart(String toolName, Map<String, Object> args, String id) {
        return new Part(null, new FunctionCall(toolName, args, id), null, null);
    }

    // ── ToolChatModel ─────────────────────────────────────

    @Override
    public Turn generate(String systemInstruction, List<Turn> conversation, List<ToolSpec> tools) {
        calls.add(new Call(systemInstruction, List.copyOf(conversation), List.copyOf(tools)));

        Object next = script.poll();
        if (next == null) {
            throw new AssertionError("FakeToolChatModel script is exhausted — the code under test made more model calls than the test scripted.");
        }
        if (next instanceof RuntimeException failure) throw failure;
        return (Turn) next;
    }

    // ── inspection ────────────────────────────────────────

    public List<Call> getCalls() {
        return calls;
    }

    public Call lastCall() {
        return calls.get(calls.size() - 1);
    }
}
