package com.bionote.notification;

import com.bionote.common.ApiException;
import com.bionote.common.PagedResponse;
import com.bionote.project.ProjectInvitationStore;
import com.bionote.project.ProjectMemberStore;
import com.bionote.project.ProjectStore;
import com.bionote.record.RecordStore;
import com.bionote.review.ReviewStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class NotificationService implements NotificationUseCase {
    private final NotificationStore notifications;private final NotificationJsonCodec json;
    private final ProjectInvitationStore invitations;private final ProjectStore projects;private final ProjectMemberStore members;
    private final RecordStore records;private final ReviewStore reviews;
    public NotificationService(NotificationStore notifications,NotificationJsonCodec json,ProjectInvitationStore invitations,
                               ProjectStore projects,ProjectMemberStore members,RecordStore records,ReviewStore reviews){
        this.notifications=notifications;this.json=json;this.invitations=invitations;this.projects=projects;
        this.members=members;this.records=records;this.reviews=reviews;
    }
    @Transactional public PagedResponse<NotificationDtos.View> list(UUID user,boolean unread,int page,int size){Instant now=Instant.now();invitations.expireDueForInvitee(user,now);page=Math.max(0,page);size=Math.max(1,Math.min(100,size));NotificationStore.PageSlice result=notifications.findByRecipient(user,unread,page,size);List<NotificationDtos.View> items=result.items().stream().map(item->{Map<String,Object> payload=json.decode(item.payloadJson());Resolved resolved=resolve(user,item.type(),payload);return new NotificationDtos.View(item.id(),item.type(),item.title(),item.body(),resolved.target(),resolved.actions(),resolved.stale(),item.createdAt(),item.readAt());}).toList();return PagedResponse.of(items,page,size,result.total());}
    @Transactional public void read(UUID user,UUID id){if(!notifications.markRead(id,user,Instant.now()))throw new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","通知不存在");}
    @Transactional public void readAll(UUID user){notifications.markAllRead(user,Instant.now());}
    public long unread(UUID user){return notifications.countUnread(user);}
    private Resolved resolve(UUID user,String type,Map<String,Object> payload){String invitation=Objects.toString(payload.get("invitationId"),null),record=Objects.toString(payload.get("recordId"),null),project=Objects.toString(payload.get("projectId"),null),review=Objects.toString(payload.get("reviewId"),null);if(invitation!=null){boolean valid=false;try{UUID id=UUID.fromString(invitation);var value=invitations.findInvitation(id).orElse(null);valid=value!=null&&value.inviteeUserId().equals(user)&&"PENDING".equals(value.status())&&value.expiresAt().isAfter(Instant.now())&&projects.findById(value.projectId()).map(item->"ACTIVE".equals(item.status())).orElse(false);}catch(IllegalArgumentException ignored){}return new Resolved(Map.of("type","INVITATION","id",invitation),valid?List.of("ACCEPT","REJECT"):List.of(),!valid);}if(record!=null){RecordStore.RecordData value=null;try{UUID id=UUID.fromString(record);value=records.findActive(id).filter(item->members.exists(item.projectId(),user)).orElse(null);}catch(IllegalArgumentException ignored){}boolean access=value!=null,stale=!access;if(access&&Set.of("REVIEW_ASSIGNED","REVIEW_REASSIGNED").contains(type)){ReviewStore.ReviewRecord assigned=null;try{assigned=review==null?null:reviews.findById(UUID.fromString(review)).orElse(null);}catch(IllegalArgumentException ignored){}stale=assigned==null||!Objects.equals(value.currentReviewId(),assigned.id())||!assigned.reviewerId().equals(user)||!"PENDING".equals(assigned.status());}if(access&&"CHANGES_REQUESTED".equals(type))stale=!value.creatorId().equals(user)||!"CHANGES_REQUESTED".equals(value.status());return new Resolved(Map.of("type","RECORD","id",record),List.of(),stale);}if(project!=null){boolean access=false;try{access=members.exists(UUID.fromString(project),user);}catch(IllegalArgumentException ignored){}return new Resolved(Map.of("type","PROJECT","id",project),List.of(),!access);}return new Resolved(Map.of("type","NONE","id",""),List.of(),true);}
    private record Resolved(Map<String,Object> target,List<String> actions,boolean stale){}
}
