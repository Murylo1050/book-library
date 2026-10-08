export type BookStatus = "LIDO" | "NAO_LIDO";

export interface Book {
  id: number;
  isbn: string | null;
  author: string | null;
  publisher: string | null;
  bookName: string;
  coverImage: string | null;
  numPages: number | null;
  status: BookStatus;
}
