package com.meddelivery.service;

import com.meddelivery.dto.request.BranchSetupRequest;
import com.meddelivery.dto.response.BranchResponse;
import com.meddelivery.dto.response.BranchStatsResponse;
import com.meddelivery.exception.BusinessException;
import com.meddelivery.exception.ResourceNotFoundException;
import com.meddelivery.model.*;
import com.meddelivery.model.enums.BranchStatus;
import com.meddelivery.model.enums.OrderStatus;
import com.meddelivery.model.enums.UserRole;
import com.meddelivery.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;
    private final BranchManagerProfileRepository branchManagerProfileRepository;
    private final PharmacyRepository pharmacyRepository;
    private final UserRepository userRepository;
    private final PharmacistRepository pharmacistRepository;
    private final PharmacyInventoryRepository inventoryRepository;
    private final OrderRepository orderRepository;
    private final InvitationService invitationService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public BranchResponse setupBranchFromInvitation(BranchSetupRequest request) {
        InvitationToken token = invitationService.validateToken(request.getToken(), InvitationService.TYPE_BRANCH_MANAGER);

        // Parse pharmacyId from token payload
        Long pharmacyId = parsePharmacyIdFromPayload(token.getPayload());
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy not found"));

        if (userRepository.findByEmail(token.getEmail()).isPresent()) {
            throw new BusinessException("An account with this email already exists.");
        }

        // Reuse existing unmanaged branch (e.g. created by migration) or create a new one
        Branch branch = branchRepository.findByNameAndPharmacyId(request.getBranchName(), pharmacyId)
                .filter(b -> b.getBranchManagerProfile() == null)
                .orElse(null);

        if (branch == null) {
            branch = Branch.builder()
                    .name(request.getBranchName())
                    .address(request.getAddress())
                    .latitude(request.getLatitude())
                    .longitude(request.getLongitude())
                    .contactInfo(StringUtils.hasText(request.getContactInfo()) ? request.getContactInfo().trim() : null)
                    .pharmacy(pharmacy)
                    .status(BranchStatus.ACTIVE)
                    .build();
        } else {
            // Update the placeholder branch with the manager's provided details
            if (StringUtils.hasText(request.getAddress())) branch.setAddress(request.getAddress());
            if (request.getLatitude() != null) branch.setLatitude(request.getLatitude());
            if (request.getLongitude() != null) branch.setLongitude(request.getLongitude());
            if (StringUtils.hasText(request.getContactInfo())) branch.setContactInfo(request.getContactInfo().trim());
            branch.setStatus(BranchStatus.ACTIVE);
        }
        branch = branchRepository.save(branch);

        // Create the branch manager user
        User user = User.builder()
                .fullName(request.getFullName())
                .email(token.getEmail())
                .role(UserRole.BRANCH_MANAGER)
                .password(passwordEncoder.encode(request.getPassword()))
                .isActive(true)
                .isVerified(true)
                .build();
        user = userRepository.save(user);

        // Create branch manager profile
        BranchManagerProfile profile = BranchManagerProfile.builder()
                .user(user)
                .branch(branch)
                .activatedAt(LocalDateTime.now())
                .build();
        branchManagerProfileRepository.save(profile);

        invitationService.markTokenUsed(request.getToken());
        log.info("Branch '{}' created under pharmacy '{}' with manager '{}'",
                branch.getName(), pharmacy.getName(), user.getEmail());

        return mapToResponse(branch);
    }

    @Transactional(readOnly = true)
    public List<BranchResponse> getBranchesForPharmacy(Long pharmacyId) {
        return branchRepository.findByPharmacyId(pharmacyId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BranchResponse getBranchDetails(Long branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + branchId));
        return mapToResponse(branch);
    }

    @Transactional
    public BranchResponse suspendBranch(Long branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + branchId));
        branch.setStatus(BranchStatus.SUSPENDED);
        return mapToResponse(branchRepository.save(branch));
    }

    @Transactional(readOnly = true)
    public BranchManagerProfile getBranchManagerProfile(Long userId) {
        return branchManagerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch manager profile not found for user: " + userId));
    }

    @Transactional(readOnly = true)
    public BranchStatsResponse getBranchStats(Long branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + branchId));

        int pharmacistCount = (int) pharmacistRepository.countByBranchId(branchId);
        List<PharmacyInventory> inventory = inventoryRepository.findByBranchId(branchId);
        int lowStock = (int) inventory.stream()
                .filter(i -> i.getLowStockThreshold() != null && i.getQuantity() <= i.getLowStockThreshold())
                .count();

        long totalOrders = orderRepository.countByAssignedPharmacistBranchId(branchId);
        long pendingOrders = orderRepository.countByAssignedPharmacistBranchIdAndStatus(branchId, OrderStatus.UPLOADED)
                + orderRepository.countByAssignedPharmacistBranchIdAndStatus(branchId, OrderStatus.MATCHING)
                + orderRepository.countByAssignedPharmacistBranchIdAndStatus(branchId, OrderStatus.ASSIGNED);
        long completedOrders = orderRepository.countByAssignedPharmacistBranchIdAndStatus(branchId, OrderStatus.COMPLETED);
        java.math.BigDecimal revenue = orderRepository.sumRevenueByBranchIdAndStatus(branchId, OrderStatus.COMPLETED);

        return BranchStatsResponse.builder()
                .branchId(branchId)
                .branchName(branch.getName())
                .pharmacistCount(pharmacistCount)
                .inventoryItems(inventory.size())
                .lowStockItems(lowStock)
                .totalOrders(totalOrders)
                .pendingOrders(pendingOrders)
                .completedOrders(completedOrders)
                .totalRevenue(revenue != null ? revenue : java.math.BigDecimal.ZERO)
                .build();
    }

    private BranchResponse mapToResponse(Branch branch) {
        BranchManagerProfile mgr = branch.getBranchManagerProfile();
        return BranchResponse.builder()
                .id(branch.getId())
                .name(branch.getName())
                .address(branch.getAddress())
                .latitude(branch.getLatitude())
                .longitude(branch.getLongitude())
                .contactInfo(branch.getContactInfo())
                .status(branch.getStatus())
                .pharmacyId(branch.getPharmacy().getId())
                .pharmacyName(branch.getPharmacy().getName())
                .managerName(mgr != null ? mgr.getUser().getFullName() : null)
                .managerEmail(mgr != null ? mgr.getUser().getEmail() : null)
                .pharmacistCount((int) pharmacistRepository.countByBranchId(branch.getId()))
                .inventoryItemCount(inventoryRepository.findByBranchId(branch.getId()).size())
                .createdAt(branch.getCreatedAt())
                .build();
    }

    private Long parsePharmacyIdFromPayload(String payload) {
        if (payload == null) throw new BusinessException("Invalid invitation token payload.");
        try {
            // Simple JSON parse: {"branchName":"...","pharmacyId":123}
            int idx = payload.indexOf("\"pharmacyId\":");
            if (idx == -1) throw new BusinessException("pharmacyId missing from invitation payload.");
            String rest = payload.substring(idx + "\"pharmacyId\":".length()).trim();
            String numStr = rest.replaceAll("[^0-9].*", "");
            return Long.parseLong(numStr);
        } catch (NumberFormatException e) {
            throw new BusinessException("Invalid pharmacyId in invitation payload.");
        }
    }
}
