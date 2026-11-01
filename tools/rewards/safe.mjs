// A Safe transaction that sends ATLAS, and the Safe Transaction Service calls that queue it for the owners.
// The proposer key is a Safe delegate: it can put a transaction in the queue, never execute one.
import { encodeFunctionData, erc20Abi, getAddress, parseUnits, zeroAddress } from 'viem';

export const SAFE_TX_TYPES = {
  SafeTx: [
    { name: 'to', type: 'address' },
    { name: 'value', type: 'uint256' },
    { name: 'data', type: 'bytes' },
    { name: 'operation', type: 'uint8' },
    { name: 'safeTxGas', type: 'uint256' },
    { name: 'baseGas', type: 'uint256' },
    { name: 'gasPrice', type: 'uint256' },
    { name: 'gasToken', type: 'address' },
    { name: 'refundReceiver', type: 'address' },
    { name: 'nonce', type: 'uint256' },
  ],
};

export function transferTx({ token, to, amount, decimals, nonce }) {
  return {
    to: getAddress(token),
    value: 0n,
    data: encodeFunctionData({ abi: erc20Abi, functionName: 'transfer', args: [getAddress(to), parseUnits(String(amount), decimals)] }),
    operation: 0,
    safeTxGas: 0n,
    baseGas: 0n,
    gasPrice: 0n,
    gasToken: zeroAddress,
    refundReceiver: zeroAddress,
    nonce: BigInt(nonce),
  };
}

/** The EIP-712 message Safe 1.3.0 and later sign; its hash is the safeTxHash the Safe app shows. */
export function safeTypedData({ safe, chainId, tx }) {
  return { domain: { chainId, verifyingContract: getAddress(safe) }, types: SAFE_TX_TYPES, primaryType: 'SafeTx', message: tx };
}

const asStrings = tx => Object.fromEntries(Object.entries(tx).map(([key, value]) => [key, typeof value === 'bigint' ? value.toString() : value]));

export function txService({ baseUrl, apiKey, fetch = globalThis.fetch }) {
  const headers = { 'Content-Type': 'application/json', ...(apiKey ? { Authorization: `Bearer ${apiKey}` } : {}) };
  const call = async (method, path, body, { missingOk = false } = {}) => {
    const response = await fetch(baseUrl.replace(/\/$/, '') + path, { method, headers, body: body && JSON.stringify(body) });
    if (missingOk && response.status === 404) return null;
    if (!response.ok) throw new Error(`Safe Transaction Service answered ${response.status} to ${method} ${path}`);
    return response.status === 201 || response.status === 204 ? null : response.json();
  };
  return {
    async safeNonce(safe) {
      return Number((await call('GET', `/api/v1/safes/${safe}/`)).nonce);
    },
    /** The next free nonce: after everything executed and everything already waiting in the queue. */
    async nextNonce(safe) {
      const executed = await this.safeNonce(safe);
      const queued = await call('GET', `/api/v1/safes/${safe}/multisig-transactions/?executed=false&ordering=-nonce&limit=1`);
      const highest = queued.results?.[0]?.nonce;
      return highest === undefined ? executed : Math.max(executed, Number(highest) + 1);
    },
    propose(safe, { tx, safeTxHash, sender, signature, origin }) {
      return call('POST', `/api/v1/safes/${safe}/multisig-transactions/`, { ...asStrings(tx), contractTransactionHash: safeTxHash, sender, signature, origin });
    },
    /** A queued or executed transaction, or null when the service no longer knows it (deleted from the queue).
     * successful stays true, false or null as reported: only an explicit false is a failure. */
    async transaction(safeTxHash) {
      const t = await call('GET', `/api/v1/multisig-transactions/${safeTxHash}/`, undefined, { missingOk: true });
      return t && { executed: t.isExecuted === true, successful: t.isSuccessful ?? null, txHash: t.transactionHash, nonce: Number(t.nonce) };
    },
    /** The safeTxHash executed at this nonce, or null when none is executed there yet. */
    async executedAt(safe, nonce) {
      const page = await call('GET', `/api/v1/safes/${safe}/multisig-transactions/?nonce=${nonce}&executed=true`);
      // Only an answer about this Safe and this nonce: a filter the service ignored must not cancel a row.
      return page.results?.find(t => t.isExecuted === true && Number(t.nonce) === Number(nonce) &&
        String(t.safe || '').toLowerCase() === String(safe).toLowerCase())?.safeTxHash ?? null;
    },
    /** The latest transactions, queued or executed: to find a reward already queued or paid before proposing it again. */
    async recent(safe) {
      // Every page: a reward transaction past the first hundred would otherwise go unseen and be queued again.
      const all = [];
      for (let offset = 0; ; offset += 100) {
        if (offset >= 5000) throw new Error('the Safe has more than 5,000 transactions; the reward check cannot read them all.');
        const page = await call('GET', `/api/v1/safes/${safe}/multisig-transactions/?ordering=-nonce&limit=100&offset=${offset}`);
        all.push(...(page.results || []));
        if (!page.next) break;
      }
      return all.map(t => ({ safeTxHash: t.safeTxHash, nonce: Number(t.nonce), origin: t.origin, executed: t.isExecuted === true,
        to: t.to, data: t.data, value: String(t.value ?? ''), operation: Number(t.operation), safeTxGas: String(t.safeTxGas ?? ''),
        baseGas: String(t.baseGas ?? ''), gasPrice: String(t.gasPrice ?? ''), gasToken: t.gasToken, refundReceiver: t.refundReceiver,
        submissionDate: t.submissionDate }));
    },
  };
}
