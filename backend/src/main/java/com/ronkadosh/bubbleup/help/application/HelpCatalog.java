package com.ronkadosh.bubbleup.help.application;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HelpCatalog {

    private static final List<HelpTopic> TOPICS = List.of(
            topic(
                    "complete-onboarding",
                    "Getting started",
                    HelpAudience.STUDENT,
                    "Why the system is locked at the beginning",
                    "Bubble.up starts locked so new students finish the setup steps that make courses, Bubbles, recommendations, and rooms useful.",
                    List.of(
                            "The lock is intentional at the beginning; it keeps you focused on the setup that powers the rest of the app.",
                            "Open Home and follow the Getting started panel.",
                            "Add your university and department in Settings.",
                            "Enroll in at least one current course from Academy.",
                            "Join or create your first Bubble.",
                            "Answer a Daily Drop to improve your matching profile. After these steps, the main app actions unlock naturally."
                    ),
                    List.of(action("Open Home", "/dashboard"), action("Open Settings", "/settings")),
                    List.of("onboarding", "locked", "start", "setup", "beginning"),
                    List.of("why locked", "system locked", "unlock", "wizard", "first steps", "begin", "blocked")
            ),
            topic(
                    "update-profile",
                    "Profile",
                    HelpAudience.STUDENT,
                    "Update your profile and study base",
                    "Your university, department, year, display name, bio, and avatar help Bubble.up show the right courses and people.",
                    List.of(
                            "Open Settings from the sidebar.",
                            "Update your display name, bio, university, department, or year.",
                            "Save the profile changes.",
                            "Use the photo button if you want to change your avatar."
                    ),
                    List.of(action("Open Settings", "/settings")),
                    List.of("profile", "settings", "avatar", "university", "department"),
                    List.of("name", "photo", "affiliation", "year", "details")
            ),
            topic(
                    "enroll-course",
                    "Academy",
                    HelpAudience.STUDENT,
                    "Enroll in a course",
                    "Courses are the base for Bubble discovery, recommendations, and course study groups.",
                    List.of(
                            "Open Academy.",
                            "Pick your department, then choose a course.",
                            "Use the term selector if you need a specific term.",
                            "Click Enroll for the current term.",
                            "Open the course page to browse Study Bubbles for that course."
                    ),
                    List.of(action("Open Academy", "/academy")),
                    List.of("academy", "course", "enroll", "study base"),
                    List.of("class", "semester", "term", "catalog", "register")
            ),
            topic(
                    "join-bubble",
                    "Bubbles",
                    HelpAudience.STUDENT,
                    "Join a Study Bubble",
                    "A Study Bubble gives you chat, calendar, files, members, and live study rooms for one course.",
                    List.of(
                            "Open My Bubbles or a course page.",
                            "Choose a public Bubble connected to a course you are enrolled in.",
                            "Click Join or Hop in.",
                            "Once you are a member, open chat, calendar, files, or live room from the Bubble hub."
                    ),
                    List.of(action("Open My Bubbles", "/groups"), action("Browse Academy", "/academy")),
                    List.of("bubble", "group", "join", "study", "members"),
                    List.of("study group", "people", "team", "classmates", "hop in")
            ),
            topic(
                    "create-bubble",
                    "Bubbles",
                    HelpAudience.STUDENT,
                    "Create a Study Bubble",
                    "Start a new Bubble when your course does not have the right study group yet.",
                    List.of(
                            "Open My Bubbles.",
                            "Click New Bubble.",
                            "Choose the department and course.",
                            "Set the name, description, public/private visibility, and member limit.",
                            "Create the Bubble. You become the owner and can invite or manage members."
                    ),
                    List.of(action("Create a Bubble", "/groups")),
                    List.of("bubble", "group", "create", "owner", "private", "public"),
                    List.of("new group", "start", "make", "invite", "max members")
            ),
            topic(
                    "bubble-chat",
                    "Bubbles",
                    HelpAudience.STUDENT,
                    "Use Bubble chat",
                    "Chat keeps study conversation, links, polls, replies, pins, and unread activity inside the Bubble.",
                    List.of(
                            "Open My Bubbles and choose a Bubble.",
                            "Use the Chat tab or large chat panel.",
                            "Write a message and press Enter to send.",
                            "Use the plus menu to share calendar events, files, or create a poll.",
                            "Pin useful messages so members can find them later."
                    ),
                    List.of(action("Open My Bubbles", "/groups")),
                    List.of("chat", "message", "poll", "pin", "reply"),
                    List.of("send", "conversation", "link", "emoji", "unread")
            ),
            topic(
                    "bubble-calendar",
                    "Bubbles",
                    HelpAudience.STUDENT,
                    "Schedule Bubble calendar events",
                    "Use the Bubble calendar for study sessions, meetings, and reminders connected to your group.",
                    List.of(
                            "Open My Bubbles and choose a Bubble.",
                            "Open the Calendar tab or panel.",
                            "Create a new event with start and end time.",
                            "Share the event to chat if members should see it immediately.",
                            "When a study room opens, use the room action from the event or dashboard."
                    ),
                    List.of(action("Open My Bubbles", "/groups")),
                    List.of("calendar", "event", "schedule", "meeting", "study room"),
                    List.of("time", "date", "reminder", "live", "session")
            ),
            topic(
                    "bubble-files",
                    "Bubbles",
                    HelpAudience.STUDENT,
                    "Share and find Bubble files",
                    "The Files panel keeps course materials, notes, and shared resources inside the Bubble.",
                    List.of(
                            "Open My Bubbles and choose a Bubble.",
                            "Open the Files tab or panel.",
                            "Upload supported files up to the app limit.",
                            "Create folders if the Bubble needs organization.",
                            "Open, preview, download, or share files to chat."
                    ),
                    List.of(action("Open My Bubbles", "/groups")),
                    List.of("files", "upload", "download", "folder", "materials"),
                    List.of("notes", "pdf", "document", "resource", "share")
            ),
            topic(
                    "live-room",
                    "Live rooms",
                    HelpAudience.STUDENT,
                    "Schedule or join a live study room",
                    "Live rooms open around scheduled study sessions so Bubble members can study together with video and whiteboard.",
                    List.of(
                            "Open the Bubble where you want to meet.",
                            "Click Create Live Bubble from the header.",
                            "Pick start time, duration, and optional description.",
                            "When the room is open, click Join Live Bubble from the Bubble header, dashboard, or event link.",
                            "Use the room chat and whiteboard while studying."
                    ),
                    List.of(action("Open My Bubbles", "/groups")),
                    List.of("live", "room", "video", "whiteboard", "schedule"),
                    List.of("call", "meeting", "jitsi", "join live", "study together")
            ),
            topic(
                    "dashboard",
                    "Home",
                    HelpAudience.STUDENT,
                    "Understand the Home feed",
                    "Home gathers live rooms, upcoming sessions, Bubble activity, unread messages, files, and discovery suggestions.",
                    List.of(
                            "Open Home from the Bubble logo.",
                            "Use Live for active rooms and sessions.",
                            "Use Upcoming for scheduled study events.",
                            "Use Activity for joins, files, messages, and unread updates.",
                            "Use Discover to preview recommended or trending Bubbles."
                    ),
                    List.of(action("Open Home", "/dashboard")),
                    List.of("home", "dashboard", "feed", "activity", "discovery"),
                    List.of("cards", "updates", "what now", "recommended", "trending")
            ),
            topic(
                    "matching-daily-drop",
                    "Matching",
                    HelpAudience.STUDENT,
                    "Improve matching with Daily Drops",
                    "Daily Drops help Bubble.up learn your study style and make better Bubble recommendations.",
                    List.of(
                            "Open the Daily Drop prompt when it appears.",
                            "Answer the short question.",
                            "Watch your profile strength improve.",
                            "When your profile is strong enough, recommendations can become matched instead of only trending."
                    ),
                    List.of(action("Open Home", "/dashboard")),
                    List.of("matching", "daily drop", "recommendations", "profile strength"),
                    List.of("quiz", "question", "matched", "trending", "reliability")
            ),
            topic(
                    "find-expert",
                    "Experts",
                    HelpAudience.STUDENT,
                    "Use the Experts tab",
                    "The Experts tab helps group owners find open expert sessions, browse verified experts, and request private help for a Bubble.",
                    List.of(
                            "Open Experts.",
                            "Use Sessions to find open sessions that your Bubble can enroll in.",
                            "Use Experts to browse verified experts by headline, bio, or expertise tag.",
                            "Open an expert profile when you want more detail.",
                            "Request a session if you need private help for a specific group or study topic.",
                            "Track sent booking requests from My bookings."
                    ),
                    List.of(action("Open Experts", "/experts"), action("Open Bookings", "/bookings")),
                    List.of("expert", "experts tab", "booking", "request", "session", "help", "mentor"),
                    List.of("mentor", "teacher", "tutor", "ask expert", "support", "private help", "open sessions", "verified experts")
            ),
            topic(
                    "expert-session-enroll",
                    "Experts",
                    HelpAudience.STUDENT,
                    "Enroll a Bubble in an expert session",
                    "If you own a Bubble, you can enroll it into an open expert session so the session appears on the Bubble calendar.",
                    List.of(
                            "Open Experts.",
                            "Choose the Sessions tab.",
                            "Find a session that matches your topic and time.",
                            "Click Enroll a group.",
                            "Pick one of the Bubbles you own.",
                            "After enrollment, open that Bubble calendar or Home to find the session link."
                    ),
                    List.of(action("Open Experts", "/experts"), action("Open My Bubbles", "/groups")),
                    List.of("expert", "session", "enroll", "bubble", "calendar", "owner"),
                    List.of("join expert session", "enroll group", "owned group", "open session", "capacity", "schedule conflict")
            ),
            topic(
                    "become-expert",
                    "Experts",
                    HelpAudience.STUDENT,
                    "Apply to become an expert",
                    "If you can help other students, create an expert profile and submit it for verification.",
                    List.of(
                            "Open Become Expert.",
                            "Fill your expert profile with bio, subjects, and availability details.",
                            "Submit the application.",
                            "After verification, open the expert hub to manage sessions and requests."
                    ),
                    List.of(action("Become an expert", "/become-expert")),
                    List.of("expert", "apply", "verification", "profile"),
                    List.of("teach", "mentor", "application", "approve", "verified")
            ),
            topic(
                    "change-appearance",
                    "Settings",
                    HelpAudience.STUDENT,
                    "Change site colors or appearance",
                    "You can switch Bubble.up between light and dark appearance from Settings. Custom accent colors are not available in the app settings yet.",
                    List.of(
                            "Open Settings from the sidebar.",
                            "Choose the Preferences tab.",
                            "In Appearance, select Light or Dark.",
                            "The choice is saved on this device and applied automatically next time.",
                            "If you mean custom brand colors instead of light/dark mode, that is not a user setting yet."
                    ),
                    List.of(action("Open Settings", "/settings")),
                    List.of("settings", "theme", "appearance", "color", "colors", "dark mode", "light mode"),
                    List.of("change color", "site color", "website color", "theme color", "dark", "light", "preferences", "look")
            ),
            topic(
                    "expert-hub",
                    "Experts",
                    HelpAudience.EXPERT,
                    "Manage your expert hub",
                    "Verified experts use the expert hub to create sessions, review bookings, and manage their public profile.",
                    List.of(
                            "Open Expert from the sidebar.",
                            "Create expert sessions for groups to join.",
                            "Review inbound booking requests.",
                            "Edit your profile when your bio, tags, or details change.",
                            "Use host controls in a session when you need to manage the whiteboard."
                    ),
                    List.of(action("Open Expert Hub", "/expert"), action("Booking requests", "/expert/requests")),
                    List.of("expert", "hub", "session", "booking", "requests"),
                    List.of("host", "manage", "profile", "verified", "whiteboard")
            )
    );

    public List<HelpTopic> all() {
        return TOPICS;
    }

    private static HelpTopic topic(
            String id,
            String category,
            HelpAudience audience,
            String title,
            String summary,
            List<String> steps,
            List<HelpAction> actions,
            List<String> tags,
            List<String> keywords
    ) {
        return new HelpTopic(id, category, audience, title, summary, steps, actions, tags, keywords);
    }

    private static HelpAction action(String label, String route) {
        return new HelpAction(label, route);
    }
}
