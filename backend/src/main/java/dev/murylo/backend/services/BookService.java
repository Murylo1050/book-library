package dev.murylo.backend.services;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import dev.murylo.backend.dtos.BookResponse;
import dev.murylo.backend.dtos.CreateBookRequest;
import dev.murylo.backend.entities.Book;
import dev.murylo.backend.exception.FileStorageException;
import dev.murylo.backend.exception.ResourceNotFoundException;
import dev.murylo.backend.repositories.BookRepository;
import jakarta.transaction.Transactional;

@Service 
public class BookService {

    private final BookRepository bookRepository;
    private final Path uploadDirectory = Paths.get("uploads/books");

    public BookService(BookRepository bookRepository){
        this.bookRepository = bookRepository;
    }


    public List<BookResponse> findAll(){
        return bookRepository.findAll()
            .stream()
            .map(book -> new BookResponse(
                    book.getId(),
                    book.getIsbn(),
                    book.getAuthor(),
                    book.getPublisher(),
                    book.getBookName(),
                    "uploads/" + book.getCoverImage(), 
                    book.getNumPages(),
                    book.getStatus()
            ))
            .toList();

    }
    

    public BookResponse create(CreateBookRequest request){

        String imagePath = saveImage(request.coverImage());

        Book book = new Book();

        book.setIsbn(request.isbn());
        book.setAuthor(request.author());
        book.setPublisher(request.publisher());
        book.setNumPages(request.numPages());
        if(request.status() != null){
            book.setStatus(request.status());
        }
        book.setBookName(request.bookName());
        book.setCoverImage(imagePath);

        Book savedBook = bookRepository.save(book);

        return new BookResponse(
                savedBook.getId(),
                savedBook.getIsbn(),
                savedBook.getAuthor(),
                savedBook.getPublisher(),
                savedBook.getBookName(),
                savedBook.getCoverImage(),
                savedBook.getNumPages(),
                savedBook.getStatus()
        );
    }

    public BookResponse update(CreateBookRequest request, Long id){

        Book book = bookRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Livro não encontrado!"));

        if(request.coverImage() != null){
            File oldFile = new File("uploads/" + book.getCoverImage());

            oldFile.delete();

            String imagePath = saveImage(request.coverImage());

            book.setCoverImage(imagePath);
        }

        book.setIsbn(request.isbn());
        book.setAuthor(request.author());
        book.setPublisher(request.publisher());
        book.setNumPages(request.numPages());
        book.setStatus(request.status());
        book.setBookName(request.bookName());
        

        Book savedBook = bookRepository.save(book);

        return new BookResponse(
                savedBook.getId(),
                savedBook.getIsbn(),
                savedBook.getAuthor(),
                savedBook.getPublisher(),
                savedBook.getBookName(),
                savedBook.getCoverImage(),
                savedBook.getNumPages(),
                savedBook.getStatus()
        );
    }

    @Transactional 
    public void delete(Long id) {
        Book book = bookRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Livro não encontrado!"));

        deleteImage(book.getCoverImage());

        bookRepository.delete(book);
    }



    private String saveImage(MultipartFile image) {

        if (image == null || image.isEmpty()) {
            return null;
        }

        try {
            Files.createDirectories(uploadDirectory);

            String extension = getExtension(image.getOriginalFilename());

            String filename =
                    UUID.randomUUID() + extension;

            Path destination =
                    uploadDirectory.resolve(filename);

            image.transferTo(destination);

            return "books/" + filename;

        } catch (IOException e) {
            throw new FileStorageException("Não foi possível salvar a imagem de capa");
        }
    }

    private void deleteImage(String imagePath) {
    if (imagePath == null) {
        return;
    }

    try {
        Path path = Paths.get("uploads", imagePath);

        Files.deleteIfExists(path);
    } catch (IOException e) {
        throw new RuntimeException("Erro ao remover imagem", e);
    }
}

    private String getExtension(String filename) {

        if (filename == null || !filename.contains(".")) {
            return "";
        }

        return filename.substring(
                filename.lastIndexOf(".")
        );
    }

}
