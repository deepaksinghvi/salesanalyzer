export interface Tenant {
  tenantId: string;
  companyName: string;
  subscriptionTier: 'Basic' | 'Pro' | 'Enterprise';
  timezone: string;
  createdAt: string;
}

export interface User {
  userId: string;
  tenantId: string;
  email: string;
  role: 'SuperAdmin' | 'Admin' | 'Viewer';
  firstName?: string;
  lastName?: string;
  active: boolean;
  createdAt: string;
}

export interface UploadJob {
  jobId: string;
  tenantId: string;
  fileName: string;
  periodType: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  rowsProcessed: number;
  errorMessage?: string;
  createdAt: string;
}

export interface SalesInsight {
  tenantId: string;
  periodMonth: string;
  categoryId: number;
  categoryName: string;
  actualRevenue: number;
  predictedRevenue: number;
  totalUnits: number;
  categoryRank: number;
}

export interface AuthUser {
  token: string;
  tokenType: string;
  userId: string;
  tenantId: string;
  email: string;
  role: string;
}
