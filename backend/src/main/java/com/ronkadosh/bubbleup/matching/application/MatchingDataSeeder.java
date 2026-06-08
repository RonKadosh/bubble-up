package com.ronkadosh.bubbleup.matching.application;

import com.ronkadosh.bubbleup.matching.model.QuizAnswerOption;
import com.ronkadosh.bubbleup.matching.model.QuizQuestion;
import com.ronkadosh.bubbleup.matching.persistence.QuizAnswerOptionRepository;
import com.ronkadosh.bubbleup.matching.persistence.QuizQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MatchingDataSeeder implements ApplicationRunner {

    private final QuizQuestionRepository questionRepository;
    private final QuizAnswerOptionRepository optionRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (questionRepository.count() > 0) return;
        seed();
    }

    private void seed() {
        // Quiz weights deliberately lean toward the three roles that behavior signals
        // can barely observe — Creative, TeamPlayer, Challenger (no clean digital action
        // maps to them; see app.matching.signals). Those roles get primary weight 0.20 /
        // secondary 0.10; the behavior-observable roles (Leader, Planner, Expert,
        // Communicator) get 0.12 / 0.06, since their score is mostly carried by behavior.
        // The quiz vector is max-normalized, so this raises how strongly a Creative /
        // TeamPlayer / Challenger lean surfaces relative to the rest.
        // Tuple order: (leader, planner, expert, creative, communicator, teamPlayer, challenger).
        addQuestion(1,
                "An exam is 3 days away and nobody has started the study guide. You…",
                "מבחן בעוד 3 ימים ועדיין אף אחד לא התחיל את סיכום החומר. אתם…",
                List.of(
                        opt("Make the doc, drop the link in chat, tag everyone",
                                "פותחים את המסמך, שולחים לינק בצ׳אט, ומתייגים את כולם",
                                0.12, 0.06, 0, 0, 0, 0, 0),
                        opt("Wait for someone to make it, then fill in the answers",
                                "מחכים שמישהו יפתח אותו, ואז משלימים את התשובות",
                                0, 0, 0.12, 0, 0, 0.10, 0),
                        opt("DM your most reliable friend to split the work",
                                "פונים בהודעה פרטית לחבר/ה הכי אמין/ה כדי לחלק עבודה",
                                0, 0, 0, 0, 0.12, 0.10, 0),
                        opt("Freak out in the chat with memes to break the tension",
                                "מתפרקים בצ׳אט עם ממים כדי לשבור את הלחץ",
                                0, 0, 0, 0.20, 0, 0, 0)));

        addQuestion(2,
                "Your group is completely stuck on a concept. You…",
                "כל הקבוצה תקועה לחלוטין על מושג מסוים. אתם…",
                List.of(
                        opt("Take charge and propose a plan of attack",
                                "לוקחים פיקוד ומציעים תוכנית פעולה",
                                0.12, 0.06, 0, 0, 0, 0, 0),
                        opt("Research deep until you find the answer",
                                "צוללים לעומק עד שמוצאים את התשובה",
                                0, 0, 0.12, 0.10, 0, 0, 0),
                        opt("Make sure everyone shares what they already know",
                                "דואגים שכל אחד ישתף מה שהוא כבר יודע",
                                0, 0, 0, 0, 0.12, 0.10, 0),
                        opt("Suggest approaching it from a completely different angle",
                                "מציעים לגשת לזה מזווית אחרת לגמרי",
                                0, 0, 0, 0.20, 0, 0, 0.10)));

        addQuestion(3,
                "Two members are arguing and nobody's moving forward. You…",
                "שני חברים מתווכחים והקבוצה לא זזה. אתם…",
                List.of(
                        opt("Step in to find a compromise",
                                "מתערבים כדי למצוא פשרה",
                                0, 0, 0, 0, 0.12, 0.10, 0),
                        opt("Push both to back up their position with evidence",
                                "דורשים משניהם לבסס את העמדה עם הוכחות",
                                0, 0, 0.06, 0, 0, 0, 0.20),
                        opt("Propose a structured way to evaluate both options",
                                "מציעים דרך מובנית להשוות בין שתי האפשרויות",
                                0.06, 0.12, 0, 0, 0, 0, 0),
                        opt("Stay positive and keep the atmosphere from curdling",
                                "שומרים על אווירה טובה כדי שהקבוצה לא תתפרק",
                                0, 0, 0, 0, 0.06, 0.20, 0)));

        addQuestion(4,
                "At the start of a study session, you naturally…",
                "בתחילת מפגש למידה, באופן טבעי אתם…",
                List.of(
                        opt("Write an agenda and share it",
                                "כותבים סדר יום ומשתפים אותו",
                                0.06, 0.12, 0, 0, 0, 0, 0),
                        opt("Ask what's done and who knows what",
                                "שואלים מה כבר נעשה ומי יודע מה",
                                0, 0, 0.06, 0, 0.12, 0, 0),
                        opt("Check in with everyone and warm things up",
                                "שואלים מה שלום כולם ושוברים את הקרח",
                                0, 0, 0, 0, 0.06, 0.20, 0),
                        opt("Dive straight into the hardest open problem",
                                "צוללים ישר לבעיה הפתוחה הכי קשה",
                                0, 0, 0.12, 0, 0, 0, 0.10)));

        addQuestion(5,
                "You realize the group has been studying the wrong chapter. You…",
                "אתם מבינים שהקבוצה למדה את הפרק הלא נכון. אתם…",
                List.of(
                        opt("Flag it immediately and redirect the group",
                                "מתריעים מיד ומפנים את הקבוצה לחומר הנכון",
                                0.12, 0, 0, 0, 0, 0, 0.10),
                        opt("Verify your sources before saying anything",
                                "מאמתים את המקורות לפני שאומרים משהו",
                                0, 0.06, 0.12, 0, 0, 0, 0),
                        opt("Gently mention it and suggest how to recover",
                                "מזכירים בעדינות ומציעים איך להתאושש",
                                0, 0, 0, 0, 0.12, 0.10, 0),
                        opt("Sketch a revised plan covering what still needs doing",
                                "מציירים תוכנית מתוקנת לכל מה שעוד צריך לעשות",
                                0.06, 0.12, 0, 0, 0, 0, 0)));

        addQuestion(6,
                "Assigned a complex project as a group, you prefer to…",
                "כשמטילים על הקבוצה פרויקט מורכב, אתם מעדיפים…",
                List.of(
                        opt("Break it into parts and assign ownership",
                                "לפרק אותו לחלקים ולחלק אחריות",
                                0.06, 0.12, 0, 0, 0, 0, 0),
                        opt("Tackle the hardest part first to unblock everyone",
                                "להתחיל מהחלק הכי קשה כדי לשחרר את כולם",
                                0, 0, 0.12, 0.10, 0, 0, 0),
                        opt("Brainstorm completely different ways to attack it",
                                "לעשות סיעור מוחות על דרכים שונות לחלוטין לתקוף את זה",
                                0, 0, 0, 0.20, 0, 0, 0.10),
                        opt("Make sure everyone understands the goal before anything else",
                                "לוודא שכולם מבינים את המטרה לפני כל דבר אחר",
                                0, 0, 0, 0, 0.12, 0.10, 0)));

        addQuestion(7,
                "Someone shares a great idea. Your first move is…",
                "מישהו משתף רעיון מצוין. הצעד הראשון שלכם…",
                List.of(
                        opt("Extend it in exciting new directions",
                                "להרחיב אותו לכיוונים חדשים ומלהיבים",
                                0, 0, 0.06, 0.20, 0, 0, 0),
                        opt("Ask what evidence supports it",
                                "לשאול אילו ראיות תומכות בו",
                                0, 0, 0.06, 0, 0, 0, 0.20),
                        opt("Turn it into a concrete action plan",
                                "להפוך אותו לתוכנית פעולה ממשית",
                                0.06, 0.12, 0, 0, 0, 0, 0),
                        opt("Make sure everyone in the group understands it",
                                "לוודא שכולם בקבוצה מבינים אותו",
                                0, 0, 0, 0, 0.12, 0.10, 0)));

        addQuestion(8,
                "The group is behind schedule. You…",
                "הקבוצה מאחרת ללוח הזמנים. אתם…",
                List.of(
                        opt("Reorganize the workload and set new mini-deadlines",
                                "מארגנים מחדש את העבודה וקובעים דד-ליינים קטנים",
                                0.06, 0.12, 0, 0, 0, 0, 0),
                        opt("Focus everyone on the most critical material",
                                "ממקדים את כולם בחומר הכי קריטי",
                                0, 0.06, 0.12, 0, 0, 0, 0),
                        opt("Motivate the group and keep morale up",
                                "מעודדים את הקבוצה ושומרים על המורל",
                                0, 0, 0, 0, 0.06, 0.20, 0),
                        opt("Propose a shortcut nobody has tried",
                                "מציעים קיצור-דרך שעדיין אף אחד לא ניסה",
                                0.06, 0, 0, 0.20, 0, 0, 0)));

        addQuestion(9,
                "One person never speaks up in group discussions. You…",
                "אדם אחד אף פעם לא מדבר בדיוני הקבוצה. אתם…",
                List.of(
                        opt("Directly invite their opinion during the session",
                                "מזמינים את דעתו/ה ישירות באמצע המפגש",
                                0, 0, 0, 0, 0.12, 0.10, 0),
                        opt("Check in with them privately to understand why",
                                "בודקים איתו/ה בארבע עיניים כדי להבין למה",
                                0, 0, 0, 0, 0.06, 0.20, 0),
                        opt("Restructure the discussion to make room for everyone",
                                "מבנים מחדש את הדיון כך שיהיה מקום לכולם",
                                0.12, 0, 0, 0, 0.06, 0, 0),
                        opt("Name it openly — the group loses value when voices are missing",
                                "אומרים את זה בגלוי — הקבוצה מאבדת כשלא שומעים את כולם",
                                0, 0, 0, 0, 0.06, 0, 0.20)));

        addQuestion(10,
                "Others in groups tend to rely on you for…",
                "אחרים בקבוצה נוטים לסמוך עליכם בענייני…",
                List.of(
                        opt("Keeping everything on track and organized",
                                "לשמור על הסדר וההתקדמות",
                                0.06, 0.12, 0, 0, 0, 0, 0),
                        opt("Knowing the subject matter deeply",
                                "להכיר את החומר לעומק",
                                0, 0, 0.12, 0, 0, 0, 0.10),
                        opt("Coming up with clever unexpected approaches",
                                "להציע גישות יצירתיות ובלתי-צפויות",
                                0, 0, 0.06, 0.20, 0, 0, 0),
                        opt("Keeping the atmosphere collaborative and positive",
                                "לשמור על אווירה שיתופית וחיובית",
                                0, 0, 0, 0, 0.06, 0.20, 0)));

        addQuestion(11,
                "When the group needs to present something, you…",
                "כשהקבוצה צריכה להציג משהו, אתם…",
                List.of(
                        opt("Volunteer to lead the presentation",
                                "מתנדבים להוביל את ההצגה",
                                0.12, 0, 0, 0, 0.06, 0, 0),
                        opt("Prepare thorough content, examples, and sources",
                                "מכינים תוכן יסודי, דוגמאות ומקורות",
                                0, 0.06, 0.12, 0, 0, 0, 0),
                        opt("Design a memorable, creative way to explain it",
                                "מעצבים דרך יצירתית וזכירה להסביר",
                                0, 0, 0, 0.20, 0.06, 0, 0),
                        opt("Make sure the explanation lands for everyone in the audience",
                                "דואגים שההסבר יגיע לכל אחד בקהל",
                                0, 0, 0, 0, 0.12, 0.10, 0)));

        addQuestion(12,
                "The group decides on an approach you think is wrong. You…",
                "הקבוצה מחליטה על גישה שאתם חושבים שהיא שגויה. אתם…",
                List.of(
                        opt("Speak up clearly and explain your concerns",
                                "מדברים בבירור ומסבירים את הסיבות לדאגה",
                                0.06, 0, 0, 0, 0, 0, 0.20),
                        opt("Propose a concrete alternative with a plan",
                                "מציעים אלטרנטיבה ממשית עם תוכנית",
                                0.06, 0.12, 0, 0, 0, 0, 0.10),
                        opt("Go with it to maintain cohesion",
                                "הולכים עם זה כדי לשמור על אחדות הקבוצה",
                                0, 0, 0, 0, 0.06, 0.20, 0),
                        opt("Ask probing questions to expose the flaw organically",
                                "שואלים שאלות חודרות שיחשפו את הבעיה באופן טבעי",
                                0, 0, 0.06, 0, 0, 0, 0.20)));
    }

    private void addQuestion(int orderIndex, String textEn, String textHe,
                              List<QuizAnswerOption> options) {
        QuizQuestion question = questionRepository.save(QuizQuestion.builder()
                .textEn(textEn)
                .textHe(textHe)
                .orderIndex(orderIndex)
                .active(true)
                .build());
        options.forEach(opt -> {
            opt.setQuestionId(question.getId());
            optionRepository.save(opt);
        });
    }

    private QuizAnswerOption opt(String textEn, String textHe,
                                  double leader, double planner, double expert,
                                  double creative, double communicator, double teamPlayer,
                                  double challenger) {
        return QuizAnswerOption.builder()
                .textEn(textEn)
                .textHe(textHe)
                .weightLeader(leader)
                .weightPlanner(planner)
                .weightExpert(expert)
                .weightCreative(creative)
                .weightCommunicator(communicator)
                .weightTeamPlayer(teamPlayer)
                .weightChallenger(challenger)
                .build();
    }
}
