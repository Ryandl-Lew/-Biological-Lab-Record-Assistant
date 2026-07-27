package com.bionote.review;

import com.bionote.collaboration.EventService;
import com.bionote.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ReviewAssignmentService {
    private final JdbcTemplate jdbc;private final EventService events;
    public ReviewAssignmentService(JdbcTemplate jdbc,EventService events){this.jdbc=jdbc;this.events=events;}

    public int reassignPending(UUID actor,UUID project,UUID currentReviewer,Map<UUID,UUID> assignments){
        List<Map<String,Object>> pending=jdbc.queryForList("SELECT v.id,v.record_id,r.creator_id,r.title,rv.revision_no FROM reviews v JOIN experiment_records r ON r.id=v.record_id JOIN record_revisions rv ON rv.id=v.revision_id WHERE r.project_id=? AND v.reviewer_id=? AND v.status='PENDING' AND r.status='IN_REVIEW'",project.toString(),currentReviewer.toString());
        if(pending.isEmpty())return 0;
        Map<UUID,UUID> safe=assignments==null?Map.of():assignments;
        Set<UUID> expected=new HashSet<>();pending.forEach(row->expected.add(UUID.fromString(row.get("id").toString())));
        if(!safe.keySet().equals(expected))throw new ApiException(HttpStatus.CONFLICT,"REVIEW_REASSIGNMENT_REQUIRED","该成员仍有待审核任务，必须逐条指定新审核人",Map.of("pendingReviewIds",expected.toString()));
        for(Map<String,Object> row:pending){UUID review=UUID.fromString(row.get("id").toString()),next=safe.get(review),creator=UUID.fromString(row.get("creator_id").toString());if(next==null||next.equals(currentReviewer)||next.equals(creator))invalid();List<String>roles=jdbc.queryForList("SELECT role FROM project_members WHERE project_id=? AND user_id=?",String.class,project.toString(),next.toString());if(roles.isEmpty()||!Set.of("OWNER","REVIEWER").contains(roles.get(0)))invalid();jdbc.update("UPDATE reviews SET reviewer_id=? WHERE id=? AND reviewer_id=? AND status='PENDING'",next.toString(),review.toString(),currentReviewer.toString());events.notify(next,"REVIEW_REASSIGNED","审核任务已重指派",row.get("title")+" 的 R"+row.get("revision_no")+" 已指派给你",Map.of("recordId",row.get("record_id").toString(),"reviewId",review.toString()),"review-reassigned:"+review+":"+next);events.notify(currentReviewer,"REVIEW_REASSIGNED_AWAY","审核任务已移交",row.get("title")+" 的审核任务已由项目负责人移交",Map.of("recordId",row.get("record_id").toString(),"reviewId",review.toString()),"review-reassigned-away:"+review+":"+currentReviewer);events.audit(actor,project,UUID.fromString(row.get("record_id").toString()),"REVIEW_REASSIGNED","REVIEW",review,Map.of("from",currentReviewer.toString(),"to",next.toString()));}
        return pending.size();
    }
    private void invalid(){throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REVIEWER","新审核人必须是同项目 OWNER 或 REVIEWER，且不能是记录创建者");}
}
