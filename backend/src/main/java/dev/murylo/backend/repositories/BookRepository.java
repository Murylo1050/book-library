package dev.murylo.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import dev.murylo.backend.entities.Book;

@Repository 
public interface BookRepository extends JpaRepository<Book, Long>{

}
