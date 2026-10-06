// The JSON the backend returns. Mirrors the response records in backend/.../model.

export type InspectionStatus = 'SENT' | 'OPENED' | 'SUBMITTED' | 'RESPONDED';

export interface Admin {
  id: string;
  email: string;
}

export interface User {
  id: string;
  email: string;
  createdAt: string;
}

export interface Client {
  id: string;
  name: string;
  carModel: string;
  phone: string;
  rego: string;
  createdAt: string;
}

export interface InspectionSummary {
  id: string;
  status: InspectionStatus;
  clientId: string;
  clientName: string;
  carModel: string;
  rego: string;
  createdAt: string;
  openedAt: string | null;
  submittedAt: string | null;
  respondedAt: string | null;
}

export interface InspectionDetail {
  inspection: InspectionSummary;
  mileage: number | null;
  conditionNotes: string | null;
  response: string | null;
}

export interface Media {
  id: string;
  contentType: string;
  sizeBytes: number;
  url: string;
}

export interface UploadTicket {
  mediaId: string;
  uploadUrl: string;
  method: string;
  contentType: string;
}
