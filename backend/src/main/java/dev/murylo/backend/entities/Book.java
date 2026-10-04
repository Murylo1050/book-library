package dev.murylo.backend.entities;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import dev.murylo.backend.enums.BookStatus;
import jakarta.persistence.*;
import lombok.*;


@Getter 
@Setter 
@AllArgsConstructor 
@NoArgsConstructor 
@Entity 
@Table(name = "books")
public class Book {


    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 13)
    private String isbn;

    @Column (length = 50)
    private String bookName;

    @Column (length = 100)
    private String author;

    @Column (length = 50)
    private String publisher;

    @Column (columnDefinition = "TEXT")
    private String coverImage;

    @Column 
    private Integer numPages;

    @Enumerated (EnumType.STRING)
    @Column (nullable = false)
    private BookStatus status = BookStatus.NAO_LIDO;


    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;


}
