import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import { CreateQuoteRequest, Quote } from '../models/quote.model';

/**
 * Cycle de vie du devis. Aucun calcul financier ici : chaque methode
 * ne fait que transmettre la requete et retourner tel quel ce que le
 * backend a calcule (RateEngine reste la seule source de verite).
 */
@Injectable({ providedIn: 'root' })
export class QuoteService {
  private readonly baseUrl = `${environment.apiBaseUrl}/v1/quotes`;

  constructor(private readonly http: HttpClient) {}

  create(request: CreateQuoteRequest): Observable<ApiResponse<Quote>> {
    return this.http.post<ApiResponse<Quote>>(this.baseUrl, request);
  }

  get(id: string): Observable<ApiResponse<Quote>> {
    return this.http.get<ApiResponse<Quote>>(`${this.baseUrl}/${id}`);
  }

  accept(id: string): Observable<ApiResponse<Quote>> {
    return this.http.post<ApiResponse<Quote>>(`${this.baseUrl}/${id}/accept`, {});
  }

  cancel(id: string): Observable<ApiResponse<Quote>> {
    return this.http.post<ApiResponse<Quote>>(`${this.baseUrl}/${id}/cancel`, {});
  }
}
