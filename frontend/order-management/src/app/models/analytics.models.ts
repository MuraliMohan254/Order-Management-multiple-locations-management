import { OrderResponse } from './pos/pos.models';

export interface TopItemDto {
  itemName: string;
  totalSold: number;
  totalRevenue?: number;
}

export interface CategoryShareDto {
  categoryName: string;
  revenue: number;
  unitsSold: number;
  percentageShare: number;
}

export interface HourlyTrafficDto {
  hour: number;
  label: string;
  orderCount: number;
  revenue: number;
}

export interface PaymentSplitDto {
  method: string;
  count: number;
  totalAmount: number;
  percentageShare: number;
}

export interface TablePerformanceDto {
  tableNumber: string;
  orderCount: number;
  totalRevenue: number;
}

export interface BusinessInsightsResponse {
  grossSales: number;
  netSales: number;
  totalDiscounts: number;
  discountRatePercent: number;
  totalOrders: number;
  billedOrders: number;
  cancelledOrders: number;
  overallAov: number;

  dineInCount: number;
  dineInRevenue: number;
  dineInAov: number;
  takeAwayCount: number;
  takeAwayRevenue: number;
  takeAwayAov: number;

  averageTurnaroundMinutes: number;
  fastServiceCount: number;
  standardServiceCount: number;
  delayedServiceCount: number;
  onTimeServiceRate: number;

  categoryShares: CategoryShareDto[];
  hourlyTraffic: HourlyTrafficDto[];
  paymentSplits: PaymentSplitDto[];
  topEarners: TopItemDto[];
  slowMovers: TopItemDto[];
  tableMetrics: TablePerformanceDto[];
}

export interface SalesDashboardResponse {
  dailySales: number;
  monthlySales: number;
  yearlySales: number;
  periodSales: number;
  totalOrdersCount: number;
  billedOrdersCount: number;
  dineInOrdersCount: number;
  takeAwayOrdersCount: number;
  averageOrderValue: number;
  averageTurnaroundMinutes: number | null;
  fastMovingItems: TopItemDto[];
  recentOrders: OrderResponse[];
}
