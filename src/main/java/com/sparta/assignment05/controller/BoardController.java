package com.sparta.assignment05.controller;

import com.sparta.assignment05.dto.request.BoardRequest;
import com.sparta.assignment05.dto.GlobalResDto;
import com.sparta.assignment05.service.BoardService;
import com.sparta.assignment05.service.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class BoardController {
    // 토큰 만료 안됨!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    private final BoardService boardService;

    @PostMapping("/auth/boards")
    public GlobalResDto<?> createBoard(@RequestPart(required = false,value = "file") MultipartFile multipartFile,
                                        @RequestPart(value = "board" ) @Valid BoardRequest boardRequest,
                                       @AuthenticationPrincipal UserDetailsImpl userDetails) throws IOException {
        return boardService.createBoard(multipartFile, boardRequest, userDetails.getMember());
    }

    @GetMapping("/boards")
    public GlobalResDto<?> getBoardsList() {
        // 댓글 개수, 좋아요 개수 리턴

        return boardService.getBoardsList();
    }

    @GetMapping("/boards/{boardId}")
    public GlobalResDto<?> getOneBoard(@PathVariable Long boardId) {
        return boardService.getOneBoard(boardId);
    }

    @PutMapping("/auth/boards/{boardId}")
    public GlobalResDto<?> updateBoard(@PathVariable Long boardId,
                                       @AuthenticationPrincipal UserDetailsImpl userDetails,
                                       @RequestBody BoardRequest boardRequest) {

        return boardService.updateBoard(boardId, userDetails.getMember(), boardRequest);
    }


    @PostMapping("/auth/boards/{boardId}/hearts")
    public GlobalResDto<?> heart(@PathVariable Long boardId,
                                 @AuthenticationPrincipal UserDetailsImpl userDetails) {

        return boardService.heart(boardId, userDetails.getMember());
    }


    @DeleteMapping("/auth/boards/{boardId}")
    public GlobalResDto<?> deleteBoard(@PathVariable Long boardId,
                                       @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return boardService.deleteBoard(boardId, userDetails.getMember());
    }








}
