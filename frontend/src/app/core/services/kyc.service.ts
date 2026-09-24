import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import { KycDocumentType, KycSubmission } from '../models/kyc.model';

/** Acces a `/api/v1/kyc/**` — soumission et suivi du dossier par le client lui-meme. */
@Injectable({ providedIn: 'root' })
export class KycService {
  private readonly baseUrl = `${environment.apiBaseUrl}/v1/kyc`;

  constructor(private readonly http: HttpClient) {}

  /** {@code data: null} si aucun dossier n'a jamais ete soumis. */
  mySubmission(): Observable<ApiResponse<KycSubmission | null>> {
    return this.http.get<ApiResponse<KycSubmission | null>>(`${this.baseUrl}/submissions/me`);
  }

  submit(documentType: KycDocumentType, front: File, back: File | null, selfie: File): Observable<ApiResponse<KycSubmission>> {
    const formData = new FormData();
    formData.append('documentType', documentType);
    formData.append('front', front);
    if (back) {
      formData.append('back', back);
    }
    formData.append('selfie', selfie);
    return this.http.post<ApiResponse<KycSubmission>>(`${this.baseUrl}/submissions`, formData);
  }
}
