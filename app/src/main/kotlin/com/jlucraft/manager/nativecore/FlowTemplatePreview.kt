package com.jlucraft.manager.nativecore

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jlucraft.manager.R
import org.json.JSONObject

@Composable
fun FlowTemplatePreview(template: JSONObject) {
    Text(template.optString("name"), style = MaterialTheme.typography.titleSmall)
    val kinds = template.optJSONArray("payload_kinds")
    val labels = (0 until (kinds?.length() ?: 0)).map { index ->
        when (val kind = kinds?.optString(index)) {
            "events.approve" -> stringResource(R.string.governance_event_approval)
            else -> kind.orEmpty()
        }
    }
    Text(stringResource(R.string.flow_allowed_actions, labels.joinToString(stringResource(R.string.governance_separator))))
    val stages = template.optJSONArray("stages") ?: return
    (0 until stages.length()).forEach { index ->
        val stage = stages.getJSONObject(index)
        val approver = stage.getJSONObject("approver")
        Text(stringResource(R.string.flow_stage_name, index + 1, stage.getString("name")))
        Text(when (approver.getString("type")) {
            "ai" -> stringResource(when (approver.optString("mode")) {
                "review" -> R.string.office_stage_review
                "generate" -> R.string.office_stage_generate
                else -> R.string.office_unknown
            })
            "member" -> stringResource(R.string.flow_member, approver.getString("member"))
            "role" -> stringResource(R.string.flow_role, approver.getString("club"), approver.getString("role"))
            "club_quorum" -> stringResource(R.string.flow_quorum, approver.getInt("quorum"), (0 until approver.getJSONArray("clubs").length()).joinToString(stringResource(R.string.governance_separator)) { approver.getJSONArray("clubs").getString(it) })
            "club_presidents" -> {
                val clubs = approver.getJSONArray("clubs")
                stringResource(
                    R.string.flow_presidents,
                    clubs.length(),
                    (0 until clubs.length()).joinToString(stringResource(R.string.governance_separator)) { clubs.getString(it) },
                )
            }
            else -> stringResource(R.string.office_unknown)
        })
        if (!stage.isNull("timeout_ms")) {
            val policy = stringResource(when (stage.getString("on_timeout")) {
                "escalate" -> R.string.flow_escalate
                "reject" -> R.string.office_reject
                "auto_approve" -> R.string.flow_autoapprove
                else -> R.string.office_unknown
            })
            Text(stringResource(R.string.flow_timeout, stage.getLong("timeout_ms") / 1000, policy))
        } else Text(stringResource(R.string.flow_no_timeout))
    }
}
