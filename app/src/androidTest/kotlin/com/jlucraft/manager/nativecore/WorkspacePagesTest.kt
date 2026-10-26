package com.jlucraft.manager.nativecore

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jlucraft.manager.R
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspacePagesTest {
    @get:Rule val compose = createComposeRule()

    private fun text(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun state(): JSONObject = JSONObject(
        """
        {
          "admins": ["admin-device"],
          "members": {
            "president": {"device": "president-device", "club": "a", "roles": ["president"]},
            "manager": {"device": "manager-device", "club": "a", "roles": ["manager"]},
            "member": {"device": "member-device", "club": "a", "roles": []}
          },
          "events": {
            "draft": {
              "title": "Draft event", "organizer": "a", "phase": "draft", "epoch": 1,
              "window": {"opens_ms": 1000, "closes_ms": 2000, "starts_ms": 3000}
            },
            "open": {
              "title": "Open event", "organizer": "a", "phase": "open", "epoch": 1,
              "window": {"opens_ms": 4000, "closes_ms": 5000, "starts_ms": 6000}
            }
          },
          "flows": {},
          "tasks": {},
          "signups": {},
          "organization": {
            "plans": {
              "draft": {
                "kind": "online",
                "games": {
                  "game-one": {"league": "linked-competition", "resource": "resource-one", "started": false}
                }
              }
            },
            "resources": {"resource-one": {"title": "Linked resource"}},
            "reports": {},
            "notices": {}
          },
          "leagues": {
            "linked-competition": {"phase": "Registration"},
            "standalone-competition": {"phase": "Playing"}
          }
        }
        """.trimIndent(),
    )

    @Test fun dashboardShowsOnlyThePresidentsRealWork() {
        compose.setContent {
            JluManagerTheme {
                WorkspaceDashboard(
                    state = state(),
                    identity = "president-device",
                    busy = false,
                    onCreateActivity = {},
                    onMembershipIssuance = {},
                    onPersonnel = {},
                    onOpenActivities = {},
                    onOpenApprovals = {},
                    onOpenReports = {},
                    onOpenResources = {},
                )
            }
        }
        compose.onNodeWithTag(OFFICE_TASK_CREATE_TAG).assertExists().assertIsEnabled()
        compose.onNodeWithTag(OFFICE_TASK_ISSUANCE_TAG).assertExists()
        compose.onNodeWithTag(OFFICE_TASK_PERSONNEL_TAG).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.workspace_dashboard_event_tasks, 1)).assertExists()
    }

    @Test fun catalogKeepsLinkedCompetitionsInsideTheirEventExactlyOnce() {
        var openedEvent = ""
        var openedCompetition = ""
        compose.setContent {
            JluManagerTheme {
                ActivityCatalog(
                    state = state(),
                    identity = "member-device",
                    busy = false,
                    onCreate = {},
                    onOpenEvent = { openedEvent = it },
                    onOpenCompetition = { openedCompetition = it },
                )
            }
        }
        compose.onAllNodesWithText("linked-competition").assertCountEquals(1)
        compose.onAllNodesWithText("standalone-competition").assertCountEquals(1)
        compose.onNodeWithTag("event:draft").scrollToIfScrollable().performClick()
        compose.runOnIdle { assertEquals("draft", openedEvent) }
        compose.onNodeWithTag("competition:linked-competition").scrollToIfScrollable().performClick()
        compose.runOnIdle { assertEquals("linked-competition", openedCompetition) }
    }

    @Test fun catalogKeepsPlannedGameReceiptWithoutOpeningAMissingLeague() {
        var openedCompetition = ""
        val gameRunState = JSONObject(
            """
            {
              "admins": [],
              "members": {},
              "events": {
                "planned": {
                  "title": "Planned event", "organizer": "a", "phase": "open", "epoch": 1,
                  "window": {"opens_ms": 1000, "closes_ms": 2000, "starts_ms": 3000}
                }
              },
              "flows": {},
              "tasks": {},
              "signups": {},
              "organization": {
                "plans": {
                  "planned": {
                    "kind": "online",
                    "games": {
                      "game-one": {
                        "reserved": true,
                        "stopped": false,
                        "resource": "resource-one",
                        "league": "planned-league",
                        "started": false,
                        "results": null,
                        "evidence": null,
                        "error": null
                      }
                    }
                  }
                },
                "resources": {"resource-one": {"title": "Linked resource"}},
                "reports": {},
                "notices": {}
              },
              "leagues": {}
            }
            """.trimIndent(),
        )
        compose.setContent {
            JluManagerTheme {
                ActivityCatalog(
                    state = gameRunState,
                    identity = "member-device",
                    busy = false,
                    onCreate = {},
                    onOpenEvent = {},
                    onOpenCompetition = { openedCompetition = it },
                )
            }
        }
        compose.onAllNodesWithText("planned-league").assertCountEquals(1)
        compose.onNodeWithTag("competition:planned-league").assertExists().assertIsNotEnabled()
        compose.runOnIdle { assertEquals("", openedCompetition) }
    }

    @Test fun createMenuRoutesThePresidentsActivityEntries() {
        var draft = 0
        var online = 0
        var meeting = 0
        compose.setContent {
            JluManagerTheme {
                ActivityCreateMenu(
                    state = state(),
                    identity = "president-device",
                    busy = false,
                    onCreateDraft = { draft++ },
                    onCreateOnline = { online++ },
                    onCreateMeeting = { meeting++ },
                    onCreateCompetition = {},
                    onBack = {},
                )
            }
        }
        compose.onNodeWithTag(ACTIVITY_CREATE_DRAFT_TAG).scrollToIfScrollable().performClick()
        compose.onNodeWithTag(ACTIVITY_CREATE_ONLINE_TAG).scrollToIfScrollable().performClick()
        compose.onNodeWithTag(ACTIVITY_CREATE_MEETING_TAG).scrollToIfScrollable().performClick()
        compose.onNodeWithTag(ACTIVITY_CREATE_COMPETITION_TAG).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, draft)
            assertEquals(1, online)
            assertEquals(1, meeting)
        }
        compose.onNodeWithTag(ACTIVITY_CREATE_DRAFT_TAG).assertIsEnabled()
    }

    @Test fun createMenuKeepsCompetitionForTheCurrentServerAdministrator() {
        var competition = 0
        compose.setContent {
            JluManagerTheme {
                ActivityCreateMenu(
                    state = state(),
                    identity = "admin-device",
                    busy = false,
                    onCreateDraft = {},
                    onCreateOnline = {},
                    onCreateMeeting = {},
                    onCreateCompetition = { competition++ },
                    onBack = {},
                )
            }
        }
        compose.onNodeWithTag(ACTIVITY_CREATE_DRAFT_TAG).assertDoesNotExist()
        compose.onNodeWithTag(ACTIVITY_CREATE_COMPETITION_TAG).scrollToIfScrollable().performClick()
        compose.runOnIdle { assertEquals(1, competition) }
    }

    @Test fun createMenuHidesEntriesForAnUnprivilegedMember() {
        compose.setContent {
            JluManagerTheme {
                ActivityCreateMenu(
                    state = state(),
                    identity = "member-device",
                    busy = false,
                    onCreateDraft = {},
                    onCreateOnline = {},
                    onCreateMeeting = {},
                    onCreateCompetition = {},
                    onBack = {},
                )
            }
        }
        compose.onNodeWithTag(ACTIVITY_CREATE_DRAFT_TAG).assertDoesNotExist()
        compose.onNodeWithTag(ACTIVITY_CREATE_ONLINE_TAG).assertDoesNotExist()
        compose.onNodeWithTag(ACTIVITY_CREATE_MEETING_TAG).assertDoesNotExist()
        compose.onNodeWithTag(ACTIVITY_CREATE_COMPETITION_TAG).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.activity_create_restricted)).assertExists()
    }
}
