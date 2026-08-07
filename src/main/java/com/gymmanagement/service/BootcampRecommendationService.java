package com.gymmanagement.service;

import com.gymmanagement.model.BootcampClass;
import com.gymmanagement.model.Member;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * BootcampRecommendationService — the final AI feature on the roadmap.
 *
 * Reuses the entire existing Gemini foundation (GeminiClient, its rate
 * limiter, the AiServiceException/GlobalExceptionHandler mapping) —
 * nothing new needed there. The only genuinely new pieces are gathering
 * the right context (a member's goal and their current enrolments) and
 * building a prompt that gives Gemini something real to reason about.
 *
 * Honest scope note: the original plan said "based on goals and
 * attendance." This system has no real attendance tracking (only
 * enrolment — whether someone's currently in a class, not whether they
 * showed up to sessions), so current enrolments stand in for that here.
 * Real attendance tracking would be its own separate feature.
 *
 * fitnessGoal is free text, not a fixed category, deliberately — it
 * gives Gemini genuine reasoning material. A goal mapped 1:1 onto a
 * bootcamp type would be a lookup table, not something that needs AI.
 */
@Service
public class BootcampRecommendationService {

    private final MemberService     memberService;
    private final MembershipService membershipService;
    private final GeminiClient      geminiClient;

    public BootcampRecommendationService(MemberService memberService,
                                         MembershipService membershipService,
                                         GeminiClient geminiClient) {
        this.memberService     = memberService;
        this.membershipService = membershipService;
        this.geminiClient      = geminiClient;
    }

    public String recommendBootcamp(String memberId) {
        Member member = memberService.getMemberById(memberId);   // throws if not found

        String goal = member.getFitnessGoal();
        if (goal == null || goal.isBlank()) {
            return "No fitness goal is set for this member yet. " +
                   "Set one via PATCH /api/members/" + memberId + "/goal, then ask again.";
        }

        List<BootcampClass> allClasses = membershipService.getAllBootcampClasses();

        List<BootcampClass> currentEnrolments = allClasses.stream()
            .filter(bc -> bc.getParticipants().contains(member))
            .collect(Collectors.toList());

        String prompt = buildPrompt(goal, currentEnrolments, allClasses);
        return geminiClient.ask(prompt);
    }

    private String buildPrompt(String goal, List<BootcampClass> currentEnrolments,
                               List<BootcampClass> allClasses) {
        StringBuilder sb = new StringBuilder();

        sb.append("A gym member has this fitness goal: \"").append(goal).append("\".\n\n");

        sb.append("They are currently enrolled in: ");
        if (currentEnrolments.isEmpty()) {
            sb.append("no bootcamp classes yet.\n\n");
        } else {
            sb.append(currentEnrolments.stream()
                .map(BootcampClass::getClassName)
                .collect(Collectors.joining(", ")))
              .append(".\n\n");
        }

        sb.append("Available bootcamp classes:\n");
        for (BootcampClass bc : allClasses) {
            sb.append("- ").append(bc.getClassName())
              .append(" (").append(bc.getSchedule()).append(", ")
              .append(bc.getCurrentEnrolments()).append("/").append(bc.getMaxCapacity())
              .append(" enrolled)\n");
        }

        sb.append("\nBased on their goal and what they're already doing, recommend ONE ")
          .append("class from the available list that would best help them, and explain ")
          .append("why in 2-3 sentences. If they're already enrolled in the best-fit class, ")
          .append("say so, and note whether adding a second class would help or if their ")
          .append("current enrolment already covers their goal well.");

        return sb.toString();
    }
}