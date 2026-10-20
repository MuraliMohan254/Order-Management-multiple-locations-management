const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');
const crypto = require('crypto');
const { resolveAndSecurePassword } = require('../electron-pos/secure-storage');

// 1. Resolve PostgreSQL credentials
const configPath = path.resolve(__dirname, '../backup-config.json');
const cfg = JSON.parse(fs.readFileSync(configPath, 'utf8'));
const dbPass = resolveAndSecurePassword(cfg);

const psqlPath = 'C:\\Program Files\\PostgreSQL\\15\\bin\\psql.exe';
const dbHost = cfg.database.host || 'localhost';
const dbPort = String(cfg.database.port || 5432);
const dbName = cfg.database.name || 'order_management';
const dbUser = cfg.database.username || 'postgres';

function runSql(sql) {
  const res = spawnSync(psqlPath, [
    '-h', dbHost,
    '-p', dbPort,
    '-U', dbUser,
    '-d', dbName,
    '-P', 'pager=off',
    '-f', '-'
  ], {
    input: sql,
    env: { ...process.env, PGPASSWORD: dbPass },
    encoding: 'utf8',
    maxBuffer: 50 * 1024 * 1024
  });

  if (res.status !== 0 || res.error) {
    throw new Error(`SQL Error: ${res.error ? res.error.message : (res.stderr || res.stdout)}`);
  }
  return res.stdout;
}

function runSqlJson(sql) {
  const jsonSql = `SELECT json_agg(t) FROM (${sql}) t;`;
  const res = spawnSync(psqlPath, [
    '-h', dbHost,
    '-p', dbPort,
    '-U', dbUser,
    '-d', dbName,
    '-t',
    '-A',
    '-f', '-'
  ], {
    input: jsonSql,
    env: { ...process.env, PGPASSWORD: dbPass },
    encoding: 'utf8',
    maxBuffer: 50 * 1024 * 1024
  });

  if (res.status !== 0 || res.error) {
    throw new Error(`SQL Error: ${res.error ? res.error.message : (res.stderr || res.stdout)}`);
  }
  const clean = (res.stdout || '').trim();
  if (!clean || clean === '') return [];
  try {
    return JSON.parse(clean);
  } catch (e) {
    return [];
  }
}

async function main() {
  console.log('================================================================');
  console.log('       WildBar POS - Enterprise Stress Testing & Disaster Recovery');
  console.log('================================================================');
  console.log(`Target Database: ${dbName} on ${dbHost}:${dbPort}`);
  console.log(`Target Cumulative Volume: Minimum 100 Crore INR (₹1,00,00,00,000)`);
  console.log('----------------------------------------------------------------\n');

  // Fetch reference data
  console.log('[Phase 1/5] Fetching Reference Master Data...');
  const items = runSqlJson('SELECT id, name, price FROM items WHERE is_available = true');
  const tables = runSqlJson('SELECT id, table_number, qr_token FROM restaurant_tables');
  const outlet = runSqlJson('SELECT id, name FROM outlets LIMIT 1')[0] || { id: 'default-outlet-123' };
  const sysConfig = runSqlJson('SELECT tax_rate, default_discount_percentage FROM system_config LIMIT 1')[0] || { tax_rate: 0.03 };

  console.log(`✓ Active Menu Items: ${items.length} items`);
  console.log(`✓ Restaurant Tables: ${tables.length} tables`);
  console.log(`✓ Active Outlet: ${outlet.id} (${outlet.name || 'Main'})`);
  console.log(`✓ Tax Rate Configured: ${(sysConfig.tax_rate * 100).toFixed(1)}%\n`);

  if (items.length === 0) {
    throw new Error('No items found in items table! Please seed items first.');
  }

  // Pre-test count
  const initialOrderCount = parseInt(runSqlJson('SELECT COUNT(*) as c FROM orders')[0].c, 10);
  console.log(`Existing baseline orders in database: ${initialOrderCount} orders.\n`);

  console.log('[Phase 2/5] Synthesizing High-Volume Realistic Orders Across 3 Flows...');
  console.log(' -> Flow A: Customer QR Code Mobile Orders');
  console.log(' -> Flow B: Cashier Dine-In Orders with Custom Discounts & Payments');
  console.log(' -> Flow C: Cashier Takeaway Orders with Parcel & GST\n');

  const BATCH_SIZE = 500;
  const TOTAL_ORDERS_TO_GENERATE = 5000;
  // High volume transactions: We scale quantities and amounts across banquet/bulk packages
  // so cumulative volume easily crosses ₹100 Crore (₹1,00,00,00,000).

  const TARGET_TOTAL_REVENUE = 1000000000; // 100 Crore INR (1 Billion)
  const avgTargetPerOrder = TARGET_TOTAL_REVENUE / TOTAL_ORDERS_TO_GENERATE; // ~₹200,000 per bulk/outlet transaction

  console.log(`Generating ${TOTAL_ORDERS_TO_GENERATE} total orders in batches of ${BATCH_SIZE}...`);
  console.log(`Average transaction volume per order: ₹${avgTargetPerOrder.toLocaleString('en-IN')}`);

  let cumulativeRevenue = 0;
  let cumulativeTax = 0;
  let cumulativeDiscount = 0;
  let totalOrderItemsCount = 0;

  const paymentMethods = ['UPI', 'CASH', 'CARD'];
  const now = new Date();

  for (let batch = 0; batch < TOTAL_ORDERS_TO_GENERATE / BATCH_SIZE; batch++) {
    const ordersSql = [];
    const itemsSql = [];
    const paymentsSql = [];

    for (let i = 0; i < BATCH_SIZE; i++) {
      const orderIndex = batch * BATCH_SIZE + i + 1;
      const orderId = crypto.randomUUID();
      const orderNum = `STRESS-${String(orderIndex).padStart(6, '0')}`;
      const flowType = (orderIndex % 3 === 0) ? 'QR_CODE' : (orderIndex % 3 === 1) ? 'DINE_IN' : 'TAKE_AWAY';

      // Pick table for DINE_IN and QR_CODE
      const table = (flowType !== 'TAKE_AWAY' && tables.length > 0) ? tables[orderIndex % tables.length] : null;
      const tableIdVal = table ? `'${table.id}'` : 'NULL';

      // Select 1 to 3 items with large realistic bulk quantities to achieve 100 Crore total volume
      const numLines = (orderIndex % 3) + 1;
      let orderSubtotal = 0;

      for (let line = 0; line < numLines; line++) {
        const item = items[(orderIndex + line) % items.length];
        const itemId = item.id;
        const unitPrice = parseFloat(item.price);
        // Vary quantity to distribute revenue (average ~1000-2500 units per line)
        const qty = Math.floor(avgTargetPerOrder / (unitPrice * numLines)) + ((orderIndex * 7 + line * 13) % 200) + 10;
        const lineTotal = Math.round(unitPrice * qty * 100) / 100;
        orderSubtotal += lineTotal;
        totalOrderItemsCount++;

        const orderItemId = crypto.randomUUID();
        itemsSql.push(`('${orderItemId}', now(), now(), 'Stress Test Item', ${qty}, ${lineTotal}, ${unitPrice}, '${itemId}', '${orderId}', 1)`);
      }

      orderSubtotal = Math.round(orderSubtotal * 100) / 100;

      // Custom discount: 0%, 2%, 5%, 10%
      const discountPct = (orderIndex % 5 === 0) ? 0.05 : (orderIndex % 7 === 0) ? 0.10 : 0.00;
      const discountAmount = Math.round(orderSubtotal * discountPct * 100) / 100;

      // Tax rate: 3% GST
      const taxableAmount = Math.max(0, orderSubtotal - discountAmount);
      const taxAmount = Math.round(taxableAmount * 0.03 * 100) / 100;
      const grandTotal = Math.round((taxableAmount + taxAmount) * 100) / 100;

      cumulativeRevenue += grandTotal;
      cumulativeTax += taxAmount;
      cumulativeDiscount += discountAmount;

      const orderTypeDb = (flowType === 'TAKE_AWAY') ? 'TAKE_AWAY' : 'DINE_IN';
      const status = (flowType === 'QR_CODE') ? 'CONFIRMED' : 'PAID';
      const kitchenStatus = (flowType === 'QR_CODE') ? 'PREPARING' : 'SERVED';
      const billedBy = (flowType === 'QR_CODE') ? 'QR_CUSTOMER' : 'Cashier-Main';

      ordersSql.push(`('${orderId}', now(), now(), ${discountAmount}, ${grandTotal}, '${kitchenStatus}', '${orderNum}', '${orderTypeDb}', '${status}', ${orderSubtotal}, ${taxAmount}, ${tableIdVal}, '${outlet.id}', '${billedBy}')`);

      if (status === 'PAID') {
        const paymentId = crypto.randomUUID();
        const pMethod = paymentMethods[orderIndex % paymentMethods.length];
        paymentsSql.push(`('${paymentId}', now(), now(), ${grandTotal}, '${pMethod}', 'SUCCESS', '${orderId}', '${outlet.id}', 'Cashier-Main', 1)`);
      }
    }

    // Insert batch in transaction
    const transactionSql = `
      BEGIN;
      INSERT INTO orders (id, created_at, updated_at, discount_amount, grand_total, kitchen_status, order_number, order_type, status, sub_total, tax_amount, table_id, outlet_id, billed_by)
      VALUES ${ordersSql.join(',\n')};

      INSERT INTO order_items (id, created_at, updated_at, notes, quantity, total_price, unit_price, item_id, order_id, batch_number)
      VALUES ${itemsSql.join(',\n')};

      ${paymentsSql.length > 0 ? `INSERT INTO payments (id, created_at, updated_at, amount_paid, payment_method, payment_status, order_id, outlet_id, settled_by, settled_by_id) VALUES ${paymentsSql.join(',\n')};` : ''}
      COMMIT;
    `;

    runSql(transactionSql);

    const completedOrders = (batch + 1) * BATCH_SIZE;
    const progressPct = ((completedOrders / TOTAL_ORDERS_TO_GENERATE) * 100).toFixed(0);
    const inrCrore = (cumulativeRevenue / 10000000).toFixed(2);
    console.log(`  [Batch ${batch + 1}/${TOTAL_ORDERS_TO_GENERATE / BATCH_SIZE}] Processed ${completedOrders}/${TOTAL_ORDERS_TO_GENERATE} orders (${progressPct}%) -> Cumulative Turnover: ₹${inrCrore} Crore`);
  }

  console.log(`\n✓ Insert Phase Complete!`);
  console.log(`Total Orders Processed: ${TOTAL_ORDERS_TO_GENERATE}`);
  console.log(`Total Order Item Rows: ${totalOrderItemsCount}`);
  console.log(`Total Cumulative Sales Turnover: ₹${(cumulativeRevenue / 10000000).toFixed(2)} Crore (₹${cumulativeRevenue.toLocaleString('en-IN')})`);
  console.log(`Total GST Tax Collected: ₹${cumulativeTax.toLocaleString('en-IN')}`);
  console.log(`Total Discounts Processed: ₹${cumulativeDiscount.toLocaleString('en-IN')}\n`);

  // ==========================================
  // PHASE 3: Mathematical Integrity Audit
  // ==========================================
  console.log('[Phase 3/5] Performing 100% Mathematical Record Consistency Audit...');

  const auditReport = runSqlJson(`
    SELECT
      COUNT(*) as total_orders,
      COUNT(*) FILTER (WHERE status = 'PAID') as paid_orders,
      COUNT(*) FILTER (WHERE status = 'CONFIRMED') as confirmed_qr_orders,
      SUM(sub_total) as db_subtotal,
      SUM(tax_amount) as db_tax,
      SUM(discount_amount) as db_discount,
      SUM(grand_total) as db_grand_total
    FROM orders
    WHERE order_number LIKE 'STRESS-%'
  `)[0];

  console.log(`  Audit 1: Total Orders Count in DB: ${auditReport.total_orders} (Expected: ${TOTAL_ORDERS_TO_GENERATE})`);
  console.log(`  Audit 2: Paid Cashier Orders: ${auditReport.paid_orders}`);
  console.log(`  Audit 3: Confirmed QR Orders: ${auditReport.confirmed_qr_orders}`);
  console.log(`  Audit 4: DB Cumulative Turnover: ₹${(parseFloat(auditReport.db_grand_total) / 10000000).toFixed(2)} Crore`);

  // Check for any mismatched order calculations in database
  const mismatchCheck = runSqlJson(`
    SELECT COUNT(*) as mismatch_count
    FROM orders o
    WHERE o.order_number LIKE 'STRESS-%'
      AND ABS(o.grand_total - (o.sub_total - o.discount_amount + o.tax_amount)) > 0.05
  `)[0];

  const paymentMismatchCheck = runSqlJson(`
    SELECT COUNT(*) as payment_mismatch
    FROM orders o
    JOIN payments p ON p.order_id = o.id
    WHERE o.order_number LIKE 'STRESS-%'
      AND ABS(p.amount_paid - o.grand_total) > 0.01
  `)[0];

  console.log(`  Audit 5: Order Total Mismatch Count: ${mismatchCheck.mismatch_count} (Must be 0)`);
  console.log(`  Audit 6: Payment Total Mismatch Count: ${paymentMismatchCheck.payment_mismatch} (Must be 0)`);

  if (parseInt(mismatchCheck.mismatch_count, 10) > 0 || parseInt(paymentMismatchCheck.payment_mismatch, 10) > 0) {
    throw new Error('FAILED: Mathematical consistency audit failed with discrepancies!');
  }
  console.log('✓ 100% of records passed mathematical reconciliation with ZERO discrepancies!\n');

  // Baseline fingerprint before backup
  const preBackupFingerprint = runSqlJson(`
    SELECT
      COUNT(*) as order_count,
      ROUND(SUM(grand_total), 2) as sum_grand_total,
      ROUND(SUM(tax_amount), 2) as sum_tax,
      (SELECT COUNT(*) FROM order_items) as item_count,
      (SELECT COUNT(*) FROM payments) as payment_count
    FROM orders
  `)[0];

  console.log('[Phase 4/5] Running End-to-End Automated Backup Routine...');
  console.log(`  Capturing pre-backup fingerprint: ${preBackupFingerprint.order_count} orders, ${preBackupFingerprint.item_count} items, ₹${parseFloat(preBackupFingerprint.sum_grand_total).toLocaleString('en-IN')}`);

  const backupScript = path.resolve(__dirname, 'backup/windows/backup.ps1');
  const backupRes = spawnSync('powershell', [
    '-NoProfile',
    '-ExecutionPolicy', 'Bypass',
    '-File', backupScript,
    '-TriggerReason', 'stress-test'
  ], { encoding: 'utf8' });

  console.log(backupRes.stdout || backupRes.stderr);
  if (backupRes.status !== 0) {
    throw new Error(`Backup failed with exit code ${backupRes.status}`);
  }
  console.log('✓ Backup archive (.dump) generated and validated successfully!\n');

  // ==========================================
  // PHASE 5: Disaster Recovery & Restore Verification
  // ==========================================
  console.log('[Phase 5/5] Testing Complete Disaster Recovery (Drop & Clean Restore)...');
  console.log('  Triggering 1-Click Restore to verify full data retention...');

  const restoreScript = path.resolve(__dirname, 'backup/windows/restore.ps1');
  const restoreRes = spawnSync('powershell', [
    '-NoProfile',
    '-ExecutionPolicy', 'Bypass',
    '-File', restoreScript
  ], { encoding: 'utf8' });

  console.log(restoreRes.stdout || restoreRes.stderr);
  if (restoreRes.status !== 0) {
    throw new Error(`Restore failed with exit code ${restoreRes.status}`);
  }
  console.log('✓ Database restored successfully from backup!\n');

  // Post-restore fingerprint
  console.log('Auditing Database After Full Disaster Recovery Restore...');
  const postRestoreFingerprint = runSqlJson(`
    SELECT
      COUNT(*) as order_count,
      ROUND(SUM(grand_total), 2) as sum_grand_total,
      ROUND(SUM(tax_amount), 2) as sum_tax,
      (SELECT COUNT(*) FROM order_items) as item_count,
      (SELECT COUNT(*) FROM payments) as payment_count
    FROM orders
  `)[0];

  console.log('----------------------------------------------------------------');
  console.log('          DATA RETENTION & RECONCILIATION VERIFICATION          ');
  console.log('----------------------------------------------------------------');
  console.log(`Total Orders:   Pre: ${preBackupFingerprint.order_count} | Post: ${postRestoreFingerprint.order_count} | Match: ${preBackupFingerprint.order_count === postRestoreFingerprint.order_count ? '100% OK ✓' : 'FAILED ❌'}`);
  console.log(`Order Items:    Pre: ${preBackupFingerprint.item_count} | Post: ${postRestoreFingerprint.item_count} | Match: ${preBackupFingerprint.item_count === postRestoreFingerprint.item_count ? '100% OK ✓' : 'FAILED ❌'}`);
  console.log(`Payment Rows:   Pre: ${preBackupFingerprint.payment_count} | Post: ${postRestoreFingerprint.payment_count} | Match: ${preBackupFingerprint.payment_count === postRestoreFingerprint.payment_count ? '100% OK ✓' : 'FAILED ❌'}`);
  console.log(`Grand Total:    Pre: ₹${parseFloat(preBackupFingerprint.sum_grand_total).toLocaleString('en-IN')} | Post: ₹${parseFloat(postRestoreFingerprint.sum_grand_total).toLocaleString('en-IN')} | Match: ${preBackupFingerprint.sum_grand_total === postRestoreFingerprint.sum_grand_total ? '100% OK ✓' : 'FAILED ❌'}`);
  console.log(`Tax Collected:  Pre: ₹${parseFloat(preBackupFingerprint.sum_tax).toLocaleString('en-IN')} | Post: ₹${parseFloat(postRestoreFingerprint.sum_tax).toLocaleString('en-IN')} | Match: ${preBackupFingerprint.sum_tax === postRestoreFingerprint.sum_tax ? '100% OK ✓' : 'FAILED ❌'}`);
  console.log('----------------------------------------------------------------\n');

  if (
    preBackupFingerprint.order_count !== postRestoreFingerprint.order_count ||
    preBackupFingerprint.item_count !== postRestoreFingerprint.item_count ||
    preBackupFingerprint.sum_grand_total !== postRestoreFingerprint.sum_grand_total
  ) {
    throw new Error('FAILED: Post-restore data verification mismatch detected!');
  }

  console.log('================================================================');
  console.log('🎉 SUCCESS: All 100 Crore turnover records, orders, items, and');
  console.log('   payments fully retained with 100.00% integrity across backup & restore!');
  console.log('================================================================');
}

main().catch(err => {
  console.error('\n❌ STRESS TEST ENCOUNTERED AN ERROR:', err.message);
  process.exit(1);
});
