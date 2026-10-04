package dev.murylo.backend.dtos;

import dev.murylo.backend.enums.BookStatus;

public record BookResponse(
    Long id,
    String isbn,
    String author,
    String publisher,
    String bookName,
    String coverImage,
    Integer numPages,
    BookStatus status 
) {

}
