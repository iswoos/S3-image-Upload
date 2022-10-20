package com.sparta.assignment05.service;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.util.IOUtils;
import com.sparta.assignment05.S3.CommonUtils;
import com.sparta.assignment05.dto.request.BoardRequest;
import com.sparta.assignment05.dto.response.BoardResponse;
import com.sparta.assignment05.dto.GlobalResDto;
import com.sparta.assignment05.dto.response.CommentResponse;
import com.sparta.assignment05.dto.response.HeartResponse;
import com.sparta.assignment05.entity.Board;
import com.sparta.assignment05.entity.Comment;
import com.sparta.assignment05.entity.Heart;
import com.sparta.assignment05.entity.Member;
import com.sparta.assignment05.repository.BoardRepository;
import com.sparta.assignment05.repository.CommentRepository;
import com.sparta.assignment05.repository.HeartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.sparta.assignment05.service.MyPageService.getGlobalResDto;


@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardRepository boardRepository;
    private final HeartRepository heartRepository;
    private final CommentRepository commentRepository;

    private final AmazonS3Client amazonS3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    @Transactional
    public GlobalResDto<?> createBoard(MultipartFile multipartFile,BoardRequest boardRequest, Member member) throws IOException {
        // 토큰 검증은 어떻게???????????????????????????
        // UserDetails 에서 멤버 객체 가져오면 예외처리 안해도 되나?????????????????????????????????????

//        if (member == null) return GlobalResDto.fail("MEMBER_NOT_FOUND","로그인이 필요합니다.");

        String imgurl = null;

        if (!multipartFile.isEmpty()) {
            String fileName = CommonUtils.buildFileName(multipartFile.getOriginalFilename());
            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentType(multipartFile.getContentType());

            byte[] bytes = IOUtils.toByteArray(multipartFile.getInputStream());
            objectMetadata.setContentLength(bytes.length);
            ByteArrayInputStream byteArrayIs = new ByteArrayInputStream(bytes);

            amazonS3Client.putObject(new PutObjectRequest(bucketName, fileName, byteArrayIs, objectMetadata)
                    .withCannedAcl(CannedAccessControlList.PublicRead));
            imgurl = amazonS3Client.getUrl(bucketName, fileName).toString();
        }

        Board board = Board.builder()
                .title(boardRequest.getTitle())
                .content(boardRequest.getContent())
                .member(member)
                .image(imgurl)
                .heartCnt(0L)
                .commentCnt(0L)
                .build();

        boardRepository.save(board);

        BoardResponse response = BoardResponse.builder()
                .boardId(board.getId())
                .title(board.getTitle())
                .content(board.getContent())
                .author(board.getMember().getEmail())
                .heartCnt(board.getHeartCnt())
                .commentCnt(board.getCommentCnt())
                .createdAt(board.getCreatedAt())
                .modifiedAt(board.getModifiedAt())
                .image(board.getImage())
                .build();


        return GlobalResDto.success(response);
    }

    @Transactional(readOnly = true)
    public GlobalResDto<?> getBoardsList() {
        List<Board> boardList = boardRepository.findAllByOrderByModifiedAtDesc();
        return getGlobalResDto(boardList);
    }

    @Transactional(readOnly = true)
    public GlobalResDto<?> getOneBoard(Long boardId) {
        Board board = isPresentPostId(boardId);
        if (board == null) return GlobalResDto.fail("NOT_EXIST_BOARD", "존재하지 않는 게시물입니다.");

        List<Comment> commentList = commentRepository.findAllByBoard(board);
//        List<String> textList = new ArrayList<>();
//        for (Comment comment : commentList) {
//            textList.add(comment.getComment());
//        }

        BoardResponse response = BoardResponse.builder()
                .boardId(boardId)
                .title(board.getTitle())
                .content(board.getContent())
                .author(board.getMember().getEmail())
                .heartCnt(board.getHeartCnt())
                .commentList(BoardResponse.commentToResponse(commentList))
                .createdAt(board.getCreatedAt())
                .modifiedAt(board.getModifiedAt())
                .build();
        return GlobalResDto.success(response);
    }

    @Transactional
    public GlobalResDto<?> updateBoard(Long boardId,
                                       Member member,
                                       BoardRequest boardRequest) {

        // 멤버 토큰 확인 해야돼 말아야돼
        Board board = isPresentPostId(boardId);
        if (board == null) {
            return GlobalResDto.fail("NOT_EXIST_BOARD", "존재하지 않는 게시물입니다.");
        }

        if (!member.getEmail().equals(board.getMember().getEmail())) {
            return GlobalResDto.fail("NO_AUTHOR", "작성자가 아닙니다.");
        }

        board.update(boardRequest);
        BoardResponse response = BoardResponse.builder()
                .boardId(board.getId())
                .title(board.getTitle())
                .content(board.getContent())
                .author(board.getMember().getEmail())
                .heartCnt(board.getHeartCnt())
                .commentCnt(board.getCommentCnt())
                .createdAt(board.getCreatedAt())
                .modifiedAt(board.getModifiedAt())
                .build();

        return GlobalResDto.success(response);
    }

    @Transactional
    public GlobalResDto<?> deleteBoard(Long boardId, Member member) {
        Board board = isPresentPostId(boardId);
        if (board == null) return GlobalResDto.fail("NOT_EXIST_BOARD", "존재하지 않는 게시물입니다.");

        if (!member.getEmail().equals(board.getMember().getEmail())) {
            return GlobalResDto.fail("NO_AUTHOR", "작성자가 아닙니다.");
        }

        // 댓글 먼저 지운 후,
        commentRepository.deleteCommentsByBoard(board);
        // 게시물 좋아요 객체도 제거
        heartRepository.deleteHeartsByBoard(board);
        // 게시물 삭제
        boardRepository.delete(board);

        return GlobalResDto.success("Deleted Data");
    }

    @Transactional
    public GlobalResDto<?> heart(Long boardId, Member member) {
        Board board = isPresentPostId(boardId);
        if (board == null) return GlobalResDto.fail("NOT_EXIST_BOARD", "존재하지 않는 게시물입니다.");

        if (heartRepository.findHeartByBoardAndMember(board,member).isEmpty()) {
            // 좋아요
            Heart heart = new Heart(board, member);
            heartRepository.save(heart);
            board.addHeart();
            return GlobalResDto.success(new HeartResponse(heart));

        } else {
            // 좋아요 취소
            Heart heart = cancelHeart(board,member);
            return GlobalResDto.success(new HeartResponse(heart));
        }
    }

    private Heart cancelHeart(Board board, Member member) {
        Heart heart = heartRepository.findHeartByBoardAndMember(board,member).get();
        board.cancelHeart();
        heartRepository.delete(heart);
        return heart;
    }




    // comment Service 에도 있는데 어떻게 처리?
    @Transactional(readOnly = true)
    Board isPresentPostId(Long boardId) {
        Optional<Board> board = boardRepository.findById(boardId);
        return board.orElse(null);
    }
}
