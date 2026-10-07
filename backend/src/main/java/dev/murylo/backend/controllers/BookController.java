package dev.murylo.backend.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.murylo.backend.dtos.BookResponse;
import dev.murylo.backend.dtos.CreateBookRequest;
import dev.murylo.backend.services.BookService;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;




@RestController 
@RequestMapping ("/api/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService){
        this.bookService = bookService;
    }


    @GetMapping()
    public ResponseEntity<List<BookResponse>> findAll() {

        List<BookResponse> books = bookService.findAll();
        return ResponseEntity
        .ok()
        .body(books);
    }
    
   

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BookResponse> create(@ModelAttribute CreateBookRequest request) {
        
        BookResponse book = bookService.create(request);
        
        return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(book);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BookResponse> update(@PathVariable Long id, @ModelAttribute CreateBookRequest request ){
        BookResponse book = bookService.update(request, id);

        return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(book);
    }

    @DeleteMapping ("/{id}")
    public ResponseEntity<Void> delete(@PathVariable  Long id){
        bookService.delete(id);

        return ResponseEntity
        .noContent()
        .build();
    } 


    
    

}
