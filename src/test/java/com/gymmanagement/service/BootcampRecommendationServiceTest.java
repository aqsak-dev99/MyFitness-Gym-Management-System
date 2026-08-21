package com.gymmanagement.service;

import com.gymmanagement.exception.MemberNotFoundException;
import com.gymmanagement.model.BootcampClass;
import com.gymmanagement.model.Member;
import com.gymmanagement.model.membership.BootcampType;
import com.gymmanagement.repository.BootcampRepository;
import com.gymmanagement.repository.FakeBootcampRepository;
import com.gymmanagement.repository.FakeMemberRepository;
import com.gymmanagement.repository.MemberRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests BootcampRecommendationService with fakes throughout — same
 * MemberRepository/BootcampRepository fakes MembershipServiceTest
 * already uses, plus FakeAiChatClient for Gemini. No network call, no
 * database, no GEMINI_API_KEY needed to run this test.
 */
class BootcampRecommendationServiceTest {

    private MemberRepository              memberRepo;
    private FakeAiChatClient              geminiClient;
    private BootcampRecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        memberRepo = new FakeMemberRepository();
        BootcampRepository bootcampRepo = new FakeBootcampRepository();
        geminiClient = new FakeAiChatClient();

        MemberService     memberService     = new MemberService(memberRepo);
        MembershipService membershipService = new MembershipService(memberRepo, bootcampRepo);

        recommendationService =
            new BootcampRecommendationService(memberService, membershipService, geminiClient);

        bootcampRepo.save(new BootcampClass("BC001", BootcampType.FITNESS_AND_ENDURANCE, "Tue/Thu 08:00", 10));
        bootcampRepo.save(new BootcampClass("BC002", BootcampType.FAT_BURN, "Mon/Wed 07:00", 10));
    }

    private Member registerMemberWithGoal(String memberId, String goal) {
        Member member = new Member("P" + memberId, memberId, "Test Member",
                                   memberId.toLowerCase() + "@email.com", "0700000000");
        member.setFitnessGoal(goal);
        memberRepo.save(member);
        return member;
    }

    /**
     * The real point of this test: no fitness goal means no Gemini call
     * at all — same "protect the quota, don't call the API for a
     * request that can't produce anything useful" principle as
     * DocumentQaService's confidence-based refusal.
     */
    @Test
    void memberWithNoFitnessGoalGetsPromptToSetOneWithoutCallingGemini() {
        Member member = new Member("P100", "M100", "Test Member", "test@email.com", "0700000000");
        memberRepo.save(member);   // no fitnessGoal set

        String result = recommendationService.recommendBootcamp("M100");

        assertTrue(result.contains("Set one via PATCH"));
        assertNull(geminiClient.getLastPromptReceived(),
            "Gemini should not be called when the member has no fitness goal set");
    }

    @Test
    void memberWithFitnessGoalReceivesGeminiRecommendation() {
        registerMemberWithGoal("M101", "I want to build endurance for a 10k race");
        geminiClient.setCannedResponse("Try the Fitness & Endurance class.");

        String result = recommendationService.recommendBootcamp("M101");

        assertEquals("Try the Fitness & Endurance class.", result);
    }

    /**
     * Confirms real gathered data reaches the prompt, not just the
     * goal alone — both the member's stated goal AND the actual
     * available class names should be present.
     */
    @Test
    void promptIncludesGoalAndAvailableBootcampClasses() {
        registerMemberWithGoal("M102", "build endurance for a 10k race");

        recommendationService.recommendBootcamp("M102");

        String actualPrompt = geminiClient.getLastPromptReceived();
        assertNotNull(actualPrompt);
        assertTrue(actualPrompt.contains("build endurance for a 10k race"));
        assertTrue(actualPrompt.contains("Bootcamp: Fitness & Endurance"));
        assertTrue(actualPrompt.contains("Bootcamp: Fat Burn"));
    }

    @Test
    void nonexistentMemberThrowsNotFoundException() {
        assertThrows(MemberNotFoundException.class, () ->
            recommendationService.recommendBootcamp("M-DOES-NOT-EXIST"));
    }
}