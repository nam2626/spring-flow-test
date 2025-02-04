package com.kh.controller;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import com.kh.dto.BoardCommentDTO;
import com.kh.dto.BoardDTO;
import com.kh.dto.BoardFileDTO;
import com.kh.dto.BoardMemberDTO;
import com.kh.service.BoardService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;




@RequestMapping("/board")
@Controller
public class BoardController {
  
  private BoardService boardService;

  public BoardController(BoardService boardService) {
    this.boardService = boardService;
  }

  @GetMapping("/{bno}")
  public ModelAndView boardDetail(@PathVariable int bno, ModelAndView view, HttpSession session) {
    //조회수 증가
    HashSet<Integer> visited = (HashSet<Integer>) session.getAttribute("visited");
    if(visited == null){
      visited = new HashSet<Integer>();
      session.setAttribute("visited", visited);
    }
    if(visited.add(bno)){
      boardService.updateBoardCount(bno);
    }

    BoardDTO board = boardService.selectBoard(bno);
    List<BoardCommentDTO> commentList = boardService.getCommentList(bno, 1);
    List<BoardFileDTO> fileList = boardService.getBoardFileList(bno);

    view.addObject("board", board);
    view.addObject("commentList", commentList);
    view.addObject("fileList", fileList);
    view.setViewName("board_view");

    return view;
  }
  
  @GetMapping("/write/view")
  public String boardView() {
      return "board_write_view";
  }
  
  @PostMapping("/write")
  public String boardWrite(BoardDTO board, HttpSession session, @RequestParam(value = "file", required = false) MultipartFile[] files) throws IllegalStateException, IOException {
      //1. 사용자가 작성한 게시글 제목, 내용, 파일 받아옴
      //2. 작성자는 세션에서 아이디만 가져옴
      String id = ((BoardMemberDTO)session.getAttribute("user")).getId();
      board.setId(id);
      //3. 게시글 새번호 받아옴
      int bno = boardService.selectBoardNo();
      board.setBno(bno);
      //4. 파일 업로드
      List<BoardFileDTO> fileList = new ArrayList<>();
      File root = new File("C:\\fileupload");
      //해당 경로가 있는지 체크, 없으면 해당 경로를 생성
      if(!root.exists()){
        root.mkdirs();
      }
      for(MultipartFile file : files){
        //파일 업로드 전에 검사
        if(file.isEmpty()){
          continue;
        }
        //업로드한 파일명
        String fileName = file.getOriginalFilename();
        //파일 저장할 경로 완성
        String filePath = root + File.separator + fileName;
        //실제 파일 저장 부분
        file.transferTo(new File(filePath));
        BoardFileDTO fileDTO = new BoardFileDTO();
        fileDTO.setBno(bno);
        fileDTO.setFpath(filePath);
        fileList.add(fileDTO);        
      }


      //5. 게시글 데이터베이스에 추가
      boardService.insertBoard(board, fileList);

      return "redirect:/board/" + board.getBno();
  }
  
  @GetMapping("/download/{fno}")
  public void fileDownload(@PathVariable int fno, HttpServletResponse response) throws IOException {
      //1. 파일 정보 DB로부터 읽어옴
      String filePath = boardService.selectFilePath(fno);
      //2. 스트림으로 파일 연결해서, 클라이언트에게 전송
      File file = new File(filePath);
      String encodingFileName = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8);
      response.setHeader("Content-Disposition",
					"attachement;filename="+encodingFileName);
			response.setHeader("Content-Transfer-Encoding", "binary");
			response.setContentLength((int)file.length());

      try(FileInputStream fis = new FileInputStream(file);
          BufferedOutputStream bos = new BufferedOutputStream(response.getOutputStream())){
            
            byte[] buffer = new byte[1024 * 1024];

            while(true){
              int count = fis.read(buffer);
              if(count == -1){
                break;
              }
              bos.write(buffer, 0, count);
              bos.flush();
            }
            
      }
  }
  


  @ResponseBody
  @GetMapping("/like/{bno}")
  public Map<String, Object> boardLike(@PathVariable int bno, HttpSession session) {
    Map<String, Object> map = new HashMap<String, Object>();
    
    if(session.getAttribute("user") == null){
      map.put("code", 2);
      map.put("msg", "로그인 하셔야 이용하실수 있습니다.");
    }else{
      String id = ((BoardMemberDTO)session.getAttribute("user")).getId();
      try {
        boardService.insertBoardLike(bno, id);
        map.put("code", 1);
        map.put("msg", "해당 게시글에 좋아요 하셨습니다.");
      } catch (Exception e) {
        boardService.deleteBoardLike(bno, id);
        map.put("code", 1);
        map.put("msg", "해당 게시글에 좋아요를 취소 하셨습니다.");
      }
      map.put("count", boardService.getBoardLike(bno));
    }
    return map;
  }

  @ResponseBody
  @GetMapping("/hate/{bno}")
  public Map<String, Object> boardHate(@PathVariable int bno, HttpSession session) {
    Map<String, Object> map = new HashMap<String, Object>();
    
    if(session.getAttribute("user") == null){
      map.put("code", 2);
      map.put("msg", "로그인 하셔야 이용하실수 있습니다.");
    }else{
      String id = ((BoardMemberDTO)session.getAttribute("user")).getId();
      try {
        boardService.insertBoardHate(bno, id);
        map.put("code", 1);
        map.put("msg", "해당 게시글에 싫어요 하셨습니다.");
      } catch (Exception e) {
        boardService.deleteBoardHate(bno, id);
        map.put("code", 1);
        map.put("msg", "해당 게시글에 싫어요를 취소 하셨습니다.");
      }
      map.put("count", boardService.getBoardHate(bno));
    }
    return map;
  }

  @GetMapping("/delete/{bno}")
  public String boardDelete(@PathVariable int bno, HttpSession session, HttpServletResponse response) {
      if(session.getAttribute("user") != null && ((BoardMemberDTO)session.getAttribute("user")).getId().equals(boardService.selectBoard(bno).getId())){
        // 첨부파일 삭제
        //1. 파일 목록 받아옴
        List<BoardFileDTO> fileList = boardService.getBoardFileList(bno);
        //2. 파일 삭제
        fileList.forEach(file -> {
          File f = new File(file.getFpath());
          f.delete();
        });
        //만약 board, board_file 테이블이 외래키로 cascade 제약조건이 설정되어있지 않다면, 직접 board_file 테이블의 데이터를 삭제해야함.
        boardService.deleteBoard(bno);

      }else{
        response.setContentType("text/html; charset=utf-8");
        try {
            response.getWriter().println(
                    "<script>alert('해당 글 작성자만 삭제가 가능합니다..'); history.back();</script>");
            return null;
        } catch (Exception e) {
            e.printStackTrace();
        }

      }
      return "redirect:/main";
  }
  
  @GetMapping("/update/view")
  public ModelAndView boardUpdateView(int bno, ModelAndView view) {
    BoardDTO board = boardService.selectBoard(bno);
    view.addObject("board", board);
    view.setViewName("board_update_view");
    return view;
  }

  @PostMapping("/update")
  public String updateBoard(BoardDTO board) {
      boardService.updateBoard(board);
      return "redirect:/board/" + board.getBno();
  }
  

  @PostMapping("/comment")
  public String boardCommentWrite(BoardCommentDTO comment, HttpSession session, HttpServletResponse response) {
      //로그인하지 않았을때
      if(session.getAttribute("user") == null){
          response.setContentType("text/html; charset=utf-8");
          try {
              response.getWriter().println(
                      "<script>alert('로그인 하셔야 이용하실수 있습니다.'); location.href='/login/view';</script>");
          } catch (Exception e) {
              e.printStackTrace();
          }
          return null;
      }



      String id = ((BoardMemberDTO)session.getAttribute("user")).getId();
      comment.setId(id);
      boardService.insertBoardComment(comment);
      
      return "redirect:/board/" + comment.getBno();
  }
  
  @GetMapping("/comment/{bno}")
  @ResponseBody
  public List<BoardCommentDTO> getMethodName(@PathVariable int bno, @RequestParam int start) {
      List<BoardCommentDTO> commentList = boardService.getCommentList(bno, start);
      return commentList;
  }
  
  @GetMapping("/comment/like/{cno}")
  @ResponseBody
  public Map<String, Object> boardCommentLike(@PathVariable int cno, HttpSession session) {
      Map<String, Object> map = new HashMap<String, Object>();
      
      if(session.getAttribute("user") == null){
          map.put("code", 2);
          map.put("msg", "로그인 하셔야 이용하실수 있습니다.");
      }else{
          String id = ((BoardMemberDTO)session.getAttribute("user")).getId();
          try {
              boardService.insertBoardCommentLike(cno, id);
              map.put("code", 1);
              map.put("msg", "해당 댓글에 좋아요 하셨습니다.");
          } catch (Exception e) {
              boardService.deleteBoardCommentLike(cno, id);
              map.put("code", 1);
              map.put("msg", "해당 댓글에 좋아요를 취소 하셨습니다.");
          }
          map.put("count", boardService.selectCommentLikeCount(cno));
      }
      return map;
    }  
  @GetMapping("/comment/hate/{cno}")
  @ResponseBody
  public Map<String, Object> boardCommentHate(@PathVariable int cno, HttpSession session) {
      Map<String, Object> map = new HashMap<String, Object>();
      
      if(session.getAttribute("user") == null){
          map.put("code", 2);
          map.put("msg", "로그인 하셔야 이용하실수 있습니다.");
      }else{
          String id = ((BoardMemberDTO)session.getAttribute("user")).getId();
          try {
              boardService.insertBoardCommentHate(cno, id);
              map.put("code", 1);
              map.put("msg", "해당 댓글에 싫어요 하셨습니다.");
          } catch (Exception e) {
              boardService.deleteBoardCommentHate(cno, id);
              map.put("code", 1);
              map.put("msg", "해당 댓글에 싫어요를 취소 하셨습니다.");
          }
          map.put("count", boardService.selectCommentHateCount(cno));
      }
      return map;
    }  

    @GetMapping("/comment/delete/{cno}")
    public String commentDelete(@PathVariable int cno, HttpSession session, HttpServletResponse response) {
      BoardCommentDTO comment = boardService.selectComment(cno);
      if(session.getAttribute("user") != null && ((BoardMemberDTO)session.getAttribute("user")).getId().equals(comment.getId())){
        boardService.deleteBoardComment(cno);
      }else{
        response.setContentType("text/html; charset=utf-8");
        try {
            response.getWriter().println(
                    "<script>alert('해당 댓글 작성자만 삭제가 가능합니다.'); history.back();</script>");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
      }
      return "redirect:/board/" + comment.getBno();
    }
    
    @PatchMapping("/comment")
    @ResponseBody
    public Map<String, Object> boardCommentUpdate(@RequestBody Map<String, String> body, HttpSession session) {
      Map<String, Object> map = new HashMap<String, Object>();
      BoardCommentDTO comment = boardService.selectComment(Integer.parseInt(body.get("cno")));
      if(session.getAttribute("user") != null && ((BoardMemberDTO)session.getAttribute("user")).getId().equals(comment.getId())){
        comment.setContent(body.get("content"));
        boardService.updateBoardComment(comment);
        map.put("code", 1);
        map.put("msg", "해당 댓글 수정 완료");
      }else{
        map.put("code", 2);
        map.put("msg", "해당 댓글 작성자만 수정이 가능합니다.");
      }
      return map;
    }
}


