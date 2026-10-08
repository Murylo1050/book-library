CREATE TABLE books (
    id BIGSERIAL PRIMARY KEY,
    isbn VARCHAR(14),
    book_name VARCHAR(50),
    author VARCHAR(100),
    publisher VARCHAR(50),
    cover_image TEXT,
    num_pages INTEGER,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);