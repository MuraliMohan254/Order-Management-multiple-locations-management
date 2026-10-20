const net = require('net');

// ESC/POS Command Byte Buffers
const ESC_INIT = Buffer.from([0x1B, 0x40]);
const ESC_ALIGN_LEFT = Buffer.from([0x1B, 0x61, 0x00]);
const ESC_ALIGN_CENTER = Buffer.from([0x1B, 0x61, 0x01]);
const ESC_ALIGN_RIGHT = Buffer.from([0x1B, 0x61, 0x02]);
const ESC_BOLD_ON = Buffer.from([0x1B, 0x45, 0x01]);
const ESC_BOLD_OFF = Buffer.from([0x1B, 0x45, 0x00]);
const ESC_DOUBLE_SIZE = Buffer.from([0x1B, 0x21, 0x30]);
const ESC_DOUBLE_HEIGHT = Buffer.from([0x1B, 0x21, 0x10]);
const ESC_NORMAL_SIZE = Buffer.from([0x1B, 0x21, 0x00]);
const ESC_CUT = Buffer.from([0x1D, 0x56, 0x41, 0x03]); // Feed 3 lines & Cut
const ESC_BEEP = Buffer.from([0x1B, 0x42, 0x02, 0x02]); // Beep 2 times

const LINE_WIDTH = 48; // 80mm paper standard width for Font A

function formatRow(left = '', right = '', width = LINE_WIDTH) {
  let l = String(left || '');
  let r = String(right || '');
  let spaces = width - l.length - r.length;
  if (spaces < 1) {
    const maxLeft = width - r.length - 2;
    if (maxLeft > 0 && l.length > maxLeft) {
      l = l.substring(0, maxLeft) + '..';
    }
    spaces = Math.max(1, width - l.length - r.length);
  }
  return Buffer.from(l + ' '.repeat(spaces) + r + '\n', 'ascii');
}

function divider(char = '-', width = LINE_WIDTH) {
  return Buffer.from(char.repeat(width) + '\n', 'ascii');
}

function textLine(str = '') {
  return Buffer.from(str + '\n', 'ascii');
}

function formatDate(date = new Date()) {
  const d = new Date(date);
  const pad = (n) => String(n).padStart(2, '0');
  const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
  return `${pad(d.getDate())}-${months[d.getMonth()]}-${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/**
 * Trims long epoch order numbers (e.g. TA-MAIN-1729351980000-A1B2 -> TA-A1B2)
 */
function trimOrderNumber(orderNumber) {
  if (!orderNumber || !String(orderNumber).trim()) return '';
  const trimmed = String(orderNumber).trim();
  const parts = trimmed.split('-');
  if (parts.length >= 3) {
    const prefix = parts[0];
    const suffix = parts[parts.length - 1];
    const hasLongTimestamp = parts.some(p => /^\d{8,}$/.test(p));
    if (hasLongTimestamp) {
      return `${prefix}-${suffix}`;
    }
  }
  if (/^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-/.test(trimmed)) {
    return trimmed.substring(0, 8).toUpperCase();
  }
  return trimmed;
}

/**
 * Sends a raw Buffer to the printer IP and port over a TCP socket.
 */
function sendRawBytes(ip, port, buffer) {
  return new Promise((resolve) => {
    const targetIp = (ip && ip.trim()) ? ip.trim() : '192.168.31.53';
    const targetPort = (port && port > 0) ? Number(port) : 9100;

    const client = new net.Socket();
    let hasFinished = false;

    client.setTimeout(3500);

    const finish = (success, message) => {
      if (hasFinished) return;
      hasFinished = true;
      try { client.destroy(); } catch (_) {}
      resolve({ success, message });
    };

    client.connect(targetPort, targetIp, () => {
      client.write(buffer, () => {
        client.end();
        finish(true, `Successfully printed to ${targetIp}:${targetPort}`);
      });
    });

    client.on('error', (err) => {
      finish(false, `Printer network error at ${targetIp}:${targetPort}: ${err.message}`);
    });

    client.on('timeout', () => {
      finish(false, `Printer timeout connecting to ${targetIp}:${targetPort} (check power/Wi-Fi)`);
    });
  });
}

/**
 * Prints a connection test receipt.
 */
async function printTestReceipt(ip, port, config) {
  const targetIp = (ip && ip.trim()) ? ip.trim() : '192.168.31.53';
  const targetPort = (port && port > 0) ? Number(port) : 9100;
  const paperSize = (config && config.thermalPaperSize) ? config.thermalPaperSize : '80mm';
  const lineWidth = paperSize === '58mm' ? 32 : 48;
  const printerName = (config && config.printerName) ? config.printerName : 'Thermal POS';

  const chunks = [
    ESC_INIT,
    ESC_ALIGN_CENTER,
    ESC_DOUBLE_SIZE,
    textLine('WILDBAR POS'),
    ESC_NORMAL_SIZE,
    textLine('Thermal Network Printer Test'),
    divider('-', lineWidth),
    ESC_ALIGN_LEFT,
    formatRow('Status:', 'CONNECTED (ONLINE)', lineWidth),
    formatRow('Printer:', printerName, lineWidth),
    formatRow('Paper Size:', paperSize, lineWidth),
    formatRow('Target IP:', targetIp, lineWidth),
    formatRow('Port:', String(targetPort), lineWidth),
    formatRow('Date/Time:', formatDate(), lineWidth),
    divider('-', lineWidth),
    ESC_ALIGN_CENTER,
    ESC_BOLD_ON,
    textLine('*** WI-FI PRINTING ACTIVE ***'),
    ESC_BOLD_OFF,
    textLine('Receipts & KOTs will print locally via Electron\n\n'),
    ESC_BEEP,
    ESC_CUT
  ];

  return sendRawBytes(targetIp, targetPort, Buffer.concat(chunks));
}

/**
 * Prints a Kitchen Order Ticket (KOT).
 */
async function printKitchenOrderTicket(ip, port, order, targetBatch, config) {
  const restName = (config && config.restaurantName) ? config.restaurantName : 'WILDBAR';
  const lineWidth = (config && config.thermalPaperSize === '58mm') ? 32 : 48;
  
  // Calculate max batch number
  let maxBatch = 1;
  const items = order.items || [];
  for (const it of items) {
    if (it.batchNumber && it.batchNumber > maxBatch) {
      maxBatch = it.batchNumber;
    }
  }

  const isFullKot = targetBatch === undefined || targetBatch === null || targetBatch === 0;
  const printBatch = isFullKot ? null : Number(targetBatch);
  const isAddOn = !isFullKot && printBatch > 1;

  const tableInfo = (order.orderType === 'DINE_IN')
    ? (order.tableNumber || (order.table ? order.table.tableNumber : '?'))
    : 'TAKEAWAY';

  const ticketTitle = isFullKot
    ? '*** FULL ORDER TICKET ***'
    : (isAddOn ? `*** ADD-ON ITEMS (ROUND ${printBatch}) ***` : '*** INITIAL ORDER (ROUND 1) ***');

  const chunks = [
    ESC_INIT,
    ESC_ALIGN_CENTER,
    ESC_DOUBLE_SIZE,
    textLine(restName),
    ESC_NORMAL_SIZE,
    ESC_BOLD_ON,
    textLine('*** KITCHEN ORDER TICKET ***'),
    textLine(ticketTitle),
    ESC_BOLD_OFF,
    ESC_DOUBLE_HEIGHT,
    textLine(tableInfo),
    ESC_NORMAL_SIZE,
    divider('-', lineWidth),
    textLine(`Order #: ${order.orderNumber || order.id || ''}`),
    ESC_ALIGN_LEFT,
    formatRow(isFullKot ? 'ALL ITEMS' : `Round: ${printBatch}`, `Time: ${formatDate()}`, lineWidth),
    divider('-', lineWidth)
  ];

  let totalQty = 0;
  for (const it of items) {
    if (!isFullKot && it.batchNumber && it.batchNumber !== printBatch) {
      continue;
    }
    const itemName = it.itemName || (it.item ? it.item.name : 'Item');
    const qty = it.quantity || 1;
    totalQty += qty;

    chunks.push(ESC_DOUBLE_HEIGHT);
    chunks.push(textLine(`${qty}x  ${itemName}`));
    chunks.push(ESC_NORMAL_SIZE);

    if (it.notes && it.notes.trim()) {
      chunks.push(textLine(`   *** NOTE: ${it.notes.trim()} ***`));
    }
  }

  chunks.push(divider('-', lineWidth));
  chunks.push(ESC_ALIGN_CENTER);
  if (isAddOn) {
    chunks.push(ESC_BOLD_ON);
    chunks.push(textLine('*** PREPARE NEW ITEMS ONLY ***'));
    chunks.push(ESC_BOLD_OFF);
  }
  chunks.push(textLine(`Total Items: ${totalQty}\n\n`));
  chunks.push(ESC_BEEP);
  chunks.push(ESC_CUT);

  return sendRawBytes(ip, port, Buffer.concat(chunks));
}

/**
 * Prints a final Customer Financial Bill/Receipt.
 */
async function printCustomerBill(ip, port, order, settledBy, config) {
  const restName = (config && config.restaurantName) ? config.restaurantName : 'WILDBAR';
  const gstNumber = (config && config.gstNumber) ? config.gstNumber : '';
  const taxName = (config && config.taxName) ? config.taxName : 'GST';
  const taxRate = (config && config.taxRate !== undefined) ? Number(config.taxRate) : 0.05;
  const lineWidth = (config && config.thermalPaperSize === '58mm') ? 32 : 48;

  const isDineIn = order.orderType === 'DINE_IN';
  const tableNumber = order.tableNumber || (order.table ? order.table.tableNumber : '?');
  const typeText = isDineIn ? `Type: Dine-In • ${tableNumber}` : 'Type: Takeaway Order';
  const fullOrderNum = order.orderNumber || order.id || '';
  const cashier = settledBy || order.billedBy || order.settledBy || '';

  const chunks = [
    ESC_INIT,
    ESC_ALIGN_CENTER,
    ESC_DOUBLE_SIZE,
    textLine(restName),
    ESC_NORMAL_SIZE,
    textLine('Order Management System'),
    divider('-', lineWidth),
    ESC_BOLD_ON,
    textLine(`Order #: ${fullOrderNum}`),
    ESC_BOLD_OFF,
    divider('-', lineWidth),
    ESC_ALIGN_LEFT,
    formatRow(typeText, `Date: ${formatDate()}`, lineWidth)
  ];

  if (gstNumber || cashier) {
    const leftText = gstNumber ? `GSTIN: ${gstNumber}` : '';
    const rightText = cashier ? `Billed By: ${cashier}` : '';
    chunks.push(formatRow(leftText, rightText, lineWidth));
  }

  chunks.push(divider('-', lineWidth));
  chunks.push(formatRow('QTY  ITEM', 'PRICE', lineWidth));
  chunks.push(divider('-', lineWidth));

  const items = order.items || [];
  for (const it of items) {
    const itemName = it.itemName || (it.item ? it.item.name : 'Item');
    const qty = it.quantity || 1;
    const price = Number(it.totalPrice || 0).toFixed(2);
    chunks.push(formatRow(`${qty}x  ${itemName}`, price, lineWidth));
  }

  chunks.push(divider('-', lineWidth));

  // Financial summary
  const subTotal = Number(order.subTotal || 0);
  const taxAmount = Number(order.taxAmount || 0);
  const discountAmount = Number(order.discountAmount || 0);
  const grandTotal = Number(order.grandTotal && discountAmount === Number(order.discountAmount || 0) ? order.grandTotal : Math.max(0, subTotal + taxAmount - discountAmount)).toFixed(2);

  chunks.push(formatRow('Subtotal:', subTotal.toFixed(2), lineWidth));

  // Only show tax row if greater than 0 and taxRate > 0
  if (Number(taxAmount) > 0 && Number(taxRate) > 0) {
    chunks.push(formatRow(`${taxName} (${Math.round(taxRate * 100)}%):`, taxAmount.toFixed(2), lineWidth));
  }

  // Only show discount if greater than 0
  if (discountAmount > 0) {
    chunks.push(formatRow('Discount:', `-${discountAmount.toFixed(2)}`, lineWidth));
  }

  chunks.push(divider('=', lineWidth));
  chunks.push(ESC_DOUBLE_HEIGHT);
  chunks.push(formatRow('TOTAL DUE:', `INR ${grandTotal}`, lineWidth));
  chunks.push(ESC_NORMAL_SIZE);
  chunks.push(divider('-', lineWidth));
  chunks.push(ESC_ALIGN_CENTER);
  chunks.push(textLine(`Thank you for dining with ${restName}!`));
  chunks.push(textLine('Visit us again soon.\n\n'));
  chunks.push(ESC_CUT);

  return sendRawBytes(ip, port, Buffer.concat(chunks));
}

module.exports = {
  sendRawBytes,
  printTestReceipt,
  printKitchenOrderTicket,
  printCustomerBill
};
