// Network check: the safeTxHash computed here must equal what Safe's own contracts compute on Base. It asks the
// canonical L2 singletons (1.3.0, 1.4.1 and 1.5.0, which the pool Safe uses) for getTransactionHash of a sample reward
// transfer; each answers with its own address as the verifying contract, which is how a Safe proxy answers too.
// POOL_SAFE=0x… also asks that Safe itself.
//   node tools/rewards/check-safe-hash.mjs [rpc-url]
import { createPublicClient, hashTypedData, http, parseAbi } from 'viem';
import { base } from 'viem/chains';
import { safeTypedData, transferTx } from './safe.mjs';

const SINGLETONS = { '1.3.0': '0x3E5c63644E683549055b9Be8653de26E0B4CD36E', '1.4.1': '0x29fcB43b46531BcA003ddC8FCB67FFE91900C762',
  '1.5.0': '0xEdd160fEBBD92E350D4D398fb636302fccd67C7e' };
if (process.env.POOL_SAFE) SINGLETONS['pool'] = process.env.POOL_SAFE;
const abi = parseAbi(['function getTransactionHash(address to, uint256 value, bytes data, uint8 operation, uint256 safeTxGas, uint256 baseGas, uint256 gasPrice, address gasToken, address refundReceiver, uint256 _nonce) view returns (bytes32)']);
const client = createPublicClient({ chain: base, transport: http(process.argv[2] || 'https://base-rpc.publicnode.com') });
const tx = transferTx({ token: '0xb462a5039a540883508ee37b5cf3999647557ba3', to: '0x52908400098527886E0F7030069857D2E4169EE7', amount: 5000000, decimals: 18, nonce: 7 });

let failed = false;
for (const [version, safe] of Object.entries(SINGLETONS)) {
  const onChain = await client.readContract({ address: safe, abi, functionName: 'getTransactionHash',
    args: [tx.to, tx.value, tx.data, tx.operation, tx.safeTxGas, tx.baseGas, tx.gasPrice, tx.gasToken, tx.refundReceiver, tx.nonce] });
  const local = hashTypedData(safeTypedData({ safe, chainId: base.id, tx }));
  console.log(`Safe ${version}: ${onChain === local ? 'match' : 'MISMATCH'} ${local}`);
  failed ||= onChain !== local;
}
process.exit(failed ? 1 : 0);
