package com.faithlog.poll.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.poll.domain.entity.PollComment;
import com.faithlog.poll.infrastructure.repository.PollCommentRepository;
import java.util.*;
import org.junit.jupiter.api.Test;

class PollCommentQueryServiceTest {
 @Test void loads_authors_once_for_many_comments_and_preserves_order_and_deleted_content() {
  var repository=mock(PollCommentRepository.class);var access=mock(PollAccessService.class);var lookup=mock(PollLookupSupport.class);
  var comments=new ArrayList<PollComment>();
  for(int i=0;i<100;i++)comments.add(PollComment.create(1L,i%2==0?7L:8L,"comment-"+i));
  comments.get(3).delete();
  when(repository.findByPollIdOrderByIdAsc(1L)).thenReturn(comments);
  var alice=new CampusUserLookupResult(7L,"Alice","alice@example.test","USER",true);
  var bob=new CampusUserLookupResult(8L,"Bob","bob@example.test","USER",true);
  when(access.getUser(7L)).thenReturn(alice);when(access.getUser(8L)).thenReturn(bob);
  when(access.getUsers(List.of(7L,8L))).thenReturn(Map.of(7L,alice,8L,bob));
  var results=new PollCommentQueryService(repository,access,lookup).listComments(2L,1L,7L);
  assertThat(results).hasSize(100);assertThat(results.get(0).name()).isEqualTo("Alice");
  assertThat(results.get(1).name()).isEqualTo("Bob");assertThat(results.get(3).content()).isEqualTo(PollComment.DELETED_CONTENT);
  verify(access).getUsers(List.of(7L,8L));verify(access,never()).getUser(any());
 }
 @Test void skips_author_lookup_for_empty_comment_list() {
  var repository=mock(PollCommentRepository.class);var access=mock(PollAccessService.class);var lookup=mock(PollLookupSupport.class);
  when(repository.findByPollIdOrderByIdAsc(1L)).thenReturn(List.of());
  assertThat(new PollCommentQueryService(repository,access,lookup).listComments(2L,1L,7L)).isEmpty();
  verifyNoInteractions(access);
 }
}
