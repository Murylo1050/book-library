package dev.murylo.backend.dtos;

import org.springframework.web.multipart.MultipartFile;

import dev.murylo.backend.enums.BookStatus;


public record CreateBookRequest(
    Long id,
    String isbn,
    String author,
    String bookName,
    String publisher,
    MultipartFile coverImage,
    Integer numPages,
    BookStatus status 
) {

}
