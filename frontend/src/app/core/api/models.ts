// Shapes of the JSON the backend returns. They mirror the Java records one to one.
// أشكال الـ JSON اللي يرجّعها الخادم. تطابق سجلات Java واحد بواحد.

export type Role = 'CANDIDATE' | 'EMPLOYER' | 'ADMIN';
export type UserStatus = 'PENDING' | 'ACTIVE' | 'SUSPENDED';
export type JobType = 'FULL_TIME' | 'PART_TIME' | 'CONTRACT' | 'INTERNSHIP';
export type JobStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED';
export type ApplicationStatus = 'SUBMITTED' | 'REVIEWING' | 'INTERVIEW' | 'OFFER' | 'REJECTED';
export type OutboxStatus = 'PENDING' | 'SENT' | 'FAILED';

export const JOB_TYPES: JobType[] = ['FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERNSHIP'];
export const APPLICATION_STATUSES: ApplicationStatus[] = [
  'SUBMITTED',
  'REVIEWING',
  'INTERVIEW',
  'OFFER',
  'REJECTED',
];
export const ROLES: Role[] = ['CANDIDATE', 'EMPLOYER', 'ADMIN'];
export const USER_STATUSES: UserStatus[] = ['PENDING', 'ACTIVE', 'SUSPENDED'];

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface User {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  status: UserStatus;
  language: string;
  companyName: string | null;
}

export interface LoginResponse {
  token: string;
  expiresAt: string;
  user: User;
}

export interface StatusResponse {
  status: string;
}

export interface JobSummary {
  id: number;
  title: string;
  companyName: string;
  city: string;
  type: JobType;
  salaryMin: number | null;
  salaryMax: number | null;
  publishedAt: string | null;
}

export interface JobDetail extends JobSummary {
  description: string;
  status: JobStatus;
}

export interface EmployerJob {
  id: number;
  title: string;
  description: string;
  city: string;
  type: JobType;
  salaryMin: number | null;
  salaryMax: number | null;
  status: JobStatus;
  createdAt: string;
  publishedAt: string | null;
  closedAt: string | null;
  applicantCount: number;
}

export interface JobRequest {
  title: string;
  description: string;
  city: string;
  type: JobType;
  salaryMin: number | null;
  salaryMax: number | null;
}

export interface TimelineEntry {
  status: ApplicationStatus;
  at: string;
}

export interface MyApplication {
  id: number;
  jobId: number;
  jobTitle: string;
  companyName: string;
  status: ApplicationStatus;
  coverNote: string | null;
  createdAt: string;
  updatedAt: string;
  timeline: TimelineEntry[];
}

export interface CandidateInfo {
  id: number;
  fullName: string;
  email: string;
  headline: string | null;
  city: string | null;
  yearsExperience: number | null;
  summary: string | null;
}

export interface Applicant {
  id: number;
  jobId: number;
  jobTitle: string;
  status: ApplicationStatus;
  coverNote: string | null;
  createdAt: string;
  updatedAt: string;
  candidate: CandidateInfo;
}

export interface Profile {
  headline: string | null;
  city: string | null;
  yearsExperience: number | null;
  summary: string | null;
}

export interface JobAlert {
  id: number;
  keyword: string | null;
  city: string | null;
  type: JobType | null;
  active: boolean;
  createdAt: string;
}

export interface AlertRequest {
  keyword: string | null;
  city: string | null;
  type: JobType | null;
}

export interface UnsubscribeResponse {
  status: string;
  stopped: number;
}

export interface AdminUser {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  status: UserStatus;
  companyName: string | null;
  createdAt: string;
  verifiedAt: string | null;
}

export interface Stats {
  usersByRole: Record<string, number>;
  usersByStatus: Record<string, number>;
  jobsByStatus: Record<string, number>;
  applicationsByStatus: Record<string, number>;
  activeAlerts: number;
  emailsByStatus: Record<string, number>;
}

export interface AdminEmail {
  id: number;
  to: string;
  template: string;
  status: OutboxStatus;
  attempts: number;
  lastError: string | null;
  createdAt: string;
  sentAt: string | null;
}
