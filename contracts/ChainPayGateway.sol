// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/**
 * @title ChainPayGateway
 * @notice Enterprise Payment Gateway Router for ChainPay Network
 * @dev Dispatches batch payouts, ERC-20 token transfers, and emits indexed on-chain events
 *      for transaction accounting.
 *
 * Security hardening (P0 fixes):
 *  1. ReentrancyGuard on all state-mutating external functions prevents cross-function
 *     re-entrancy attacks where a malicious recipient contract re-enters dispatchBatchPayout
 *     mid-loop and drains additional ETH beyond their allocation.
 *  2. msg.value sufficiency check moved BEFORE any loop iteration (Checks-Effects-Interactions).
 *     Previously the check fired AFTER all transfers had already executed, making it useless.
 */
contract ReentrancyGuard {
    uint256 private constant _NOT_ENTERED = 1;
    uint256 private constant _ENTERED = 2;
    uint256 private _status;

    constructor() {
        _status = _NOT_ENTERED;
    }

    modifier nonReentrant() {
        require(_status != _ENTERED, "ReentrancyGuard: reentrant call");
        _status = _ENTERED;
        _;
        _status = _NOT_ENTERED;
    }
}

contract ChainPayGateway is ReentrancyGuard {

    address public owner;

    event PayoutDispatched(
        bytes32 indexed payoutId,
        address indexed merchant,
        address indexed asset,
        address recipient,
        uint256 amount,
        string memo,
        uint256 timestamp
    );

    event BatchPayoutDispatched(
        bytes32 indexed batchId,
        uint256 totalCount,
        uint256 totalAmount,
        uint256 timestamp
    );

    modifier onlyOwner() {
        require(msg.sender == owner, "ChainPayGateway: caller is not the owner");
        _;
    }

    constructor() {
        owner = msg.sender;
    }

    /**
     * @notice Dispatch a single native ETH payout with an on-chain reference memo.
     * @dev Protected by nonReentrant to prevent re-entrancy via the recipient's fallback.
     */
    function dispatchNativePayout(
        bytes32 payoutId,
        address payable recipient,
        string calldata memo
    ) external payable onlyOwner nonReentrant {
        require(msg.value > 0, "ChainPayGateway: invalid transfer amount");
        (bool success, ) = recipient.call{value: msg.value}("");
        require(success, "ChainPayGateway: transfer failed");

        emit PayoutDispatched(
            payoutId,
            msg.sender,
            address(0),
            recipient,
            msg.value,
            memo,
            block.timestamp
        );
    }

    /**
     * @notice Dispatch a batch of native ETH payouts in a single transaction.
     * @dev Re-entrancy hardening:
     *      - nonReentrant guard prevents cross-function re-entrancy.
     *      - msg.value sufficiency is verified BEFORE any transfer executes (Checks-Effects-Interactions).
     *        The previous implementation checked msg.value AFTER all transfers, making the check ineffective.
     */
    function dispatchBatchPayout(
        bytes32 batchId,
        bytes32[] calldata payoutIds,
        address payable[] calldata recipients,
        uint256[] calldata amounts,
        string[] calldata memos
    ) external payable onlyOwner nonReentrant {
        require(
            payoutIds.length == recipients.length &&
            recipients.length == amounts.length &&
            amounts.length == memos.length,
            "ChainPayGateway: array length mismatch"
        );
        require(payoutIds.length > 0, "ChainPayGateway: empty batch");

        // --- CHECKS ---
        // Pre-compute total required and verify msg.value BEFORE any external call.
        // This is the Checks-Effects-Interactions pattern: validate all invariants first.
        uint256 totalRequired = 0;
        for (uint256 i = 0; i < amounts.length; i++) {
            require(amounts[i] > 0, "ChainPayGateway: invalid transfer amount");
            totalRequired += amounts[i];
        }
        require(msg.value >= totalRequired, "ChainPayGateway: insufficient msg.value for batch");

        // --- INTERACTIONS ---
        // Only after all checks pass do we perform external calls.
        uint256 totalDispatched = 0;
        for (uint256 i = 0; i < recipients.length; i++) {
            (bool success, ) = recipients[i].call{value: amounts[i]}("");
            require(success, "ChainPayGateway: batch transfer failed");

            totalDispatched += amounts[i];

            emit PayoutDispatched(
                payoutIds[i],
                msg.sender,
                address(0),
                recipients[i],
                amounts[i],
                memos[i],
                block.timestamp
            );
        }

        emit BatchPayoutDispatched(
            batchId,
            recipients.length,
            totalDispatched,
            block.timestamp
        );
    }
}
