export interface CategoryResponse {
  id: string;
  name: string;
  active: boolean;
}

export interface CategoryRequest {
  name: string;
  active: boolean;
}

export interface ItemResponse {
  id: string;
  categoryId: string;
  categoryName: string;
  name: string;
  description?: string;
  price: number;
  available: boolean;
}

export interface ItemRequest {
  categoryId: string;
  name: string;
  description?: string;
  price: number;
  available: boolean;
}