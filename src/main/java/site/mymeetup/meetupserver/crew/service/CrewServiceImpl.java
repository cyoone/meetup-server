package site.mymeetup.meetupserver.crew.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import site.mymeetup.meetupserver.common.service.S3ImageService;
import site.mymeetup.meetupserver.crew.entity.Crew;
import site.mymeetup.meetupserver.crew.entity.CrewLike;
import site.mymeetup.meetupserver.crew.entity.CrewMember;
import site.mymeetup.meetupserver.crew.repository.CrewLikeRepository;
import site.mymeetup.meetupserver.crew.repository.CrewMemberRepository;
import site.mymeetup.meetupserver.crew.repository.CrewRepository;
import site.mymeetup.meetupserver.crew.role.CrewMemberRole;
import site.mymeetup.meetupserver.exception.CustomException;
import site.mymeetup.meetupserver.exception.ErrorCode;
import site.mymeetup.meetupserver.geo.entity.Geo;
import site.mymeetup.meetupserver.geo.repository.GeoRepository;
import site.mymeetup.meetupserver.interest.entity.InterestBig;
import site.mymeetup.meetupserver.interest.entity.InterestSmall;
import site.mymeetup.meetupserver.interest.repository.InterestBigRepository;
import site.mymeetup.meetupserver.interest.repository.InterestSmallRepository;
import site.mymeetup.meetupserver.member.entity.Member;
import site.mymeetup.meetupserver.member.repository.MemberRepository;
import static site.mymeetup.meetupserver.crew.dto.CrewDto.CrewSaveReqDto;
import static site.mymeetup.meetupserver.crew.dto.CrewDto.CrewSaveRespDto;
import static site.mymeetup.meetupserver.crew.dto.CrewDto.CrewSelectRespDto;
import static site.mymeetup.meetupserver.crew.dto.CrewMemberDto.CrewMemberSaveReqDto;
import static site.mymeetup.meetupserver.crew.dto.CrewMemberDto.CrewMemberSaveRespDto;
import static site.mymeetup.meetupserver.crew.dto.CrewMemberDto.CrewMemberSelectRespDto;
import static site.mymeetup.meetupserver.crew.dto.CrewLikeDto.CrewLikeSaveRespDto;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CrewServiceImpl implements CrewService {
    private final S3ImageService s3ImageService;
    private final CrewRepository crewRepository;
    private final GeoRepository geoRepository;
    private final InterestBigRepository interestBigRepository;
    private final InterestSmallRepository interestSmallRepository;
    private final MemberRepository memberRepository;
    private final CrewMemberRepository crewMemberRepository;
    private final CrewLikeRepository crewLikeRepository;

    // 모임 등록
    public CrewSaveRespDto createCrew(CrewSaveReqDto crewSaveReqDto, MultipartFile image) {
        // geoId로 Geo 객체 조회
        Geo geo = geoRepository.findById(crewSaveReqDto.getGeoId())
                .orElseThrow(() -> new CustomException(ErrorCode.GEO_NOT_FOUND));

        // interestBigId로 InterestBig 객체 조회
        InterestBig interestBig = interestBigRepository.findById(crewSaveReqDto.getInterestBigId())
                .orElseThrow(() -> new CustomException(ErrorCode.INTEREST_BIG_NOT_FOUND));

        // interestSmallId로 InterestSmall 객체 조회
        InterestSmall interestSmall = null;
        if (crewSaveReqDto.getInterestSmallId() != null) {
            interestSmall  = interestSmallRepository.findById(crewSaveReqDto.getInterestSmallId())
                    .orElseThrow(() -> new CustomException(ErrorCode.INTEREST_SMALL_NOT_FOUND));

            // interestSmall의 interestBig 값이 interestBig와 같은지 확인
            if (interestSmall.getInterestBig().getInterestBigId() != interestBig.getInterestBigId()) {
                throw new CustomException(ErrorCode.INTEREST_BAD_REQUEST);
            }
        }

        // S3 이미지 업로드
        String originalImg = null;
        String saveImg = null;
        if (!image.isEmpty()) {
            saveImg = s3ImageService.upload(image);
            originalImg = image.getOriginalFilename();
        }

        // dto -> entity
        Crew crew = crewRepository.save(crewSaveReqDto.toEntity(geo, interestBig, interestSmall, originalImg, saveImg));

        // 모임 멤버 등록
        // 현재 로그인 한 사용자 정보 가져오기
        Long memberId = 101L;   // 테스트용
        Member member = memberRepository.findById(memberId) // 나중에 상태값까지 비교
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        CrewMember crewMember = CrewMember.builder()
                .role(CrewMemberRole.LEADER)
                .crew(crew)
                .member(member)
                .build();
        crewMemberRepository.save(crewMember);

        return CrewSaveRespDto.builder().crew(crew).build();
    }

    // 모임 수정
    public CrewSaveRespDto updateCrew(Long crewId, CrewSaveReqDto crewSaveReqDto, MultipartFile image) {
        // 현재 로그인 한 사용자 정보 가져오기
        Long memberId = 101L;   // 테스트용
        Member member = memberRepository.findByMemberIdAndStatus(memberId, 1) // 나중에 상태값까지 비교
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        // 해당 모임이 존재하는지 검증
        Crew crew = crewRepository.findByCrewIdAndStatus(crewId, 1)
                .orElseThrow(() -> new CustomException(ErrorCode.CREW_NOT_FOUND));

        // 해당 모임의 모임장인지 확인
        if (!crewMemberRepository.existsByCrewAndMemberAndRole(crew, member, CrewMemberRole.LEADER)) {
            throw new CustomException(ErrorCode.CREW_ACCESS_DENIED);
        }

        // geoId로 Geo 객체 조회
        Geo geo = geoRepository.findById(crewSaveReqDto.getGeoId())
                .orElseThrow(() -> new CustomException(ErrorCode.GEO_NOT_FOUND));

        // interestBigId로 InterestBig 객체 조회
        InterestBig interestBig = interestBigRepository.findById(crewSaveReqDto.getInterestBigId())
                .orElseThrow(() -> new CustomException(ErrorCode.INTEREST_BIG_NOT_FOUND));

        // interestSmallId로 InterestSmall 객체 조회
        InterestSmall interestSmall = null;
        if (crewSaveReqDto.getInterestSmallId() != null) {
            interestSmall  = interestSmallRepository.findById(crewSaveReqDto.getInterestSmallId())
                    .orElseThrow(() -> new CustomException(ErrorCode.INTEREST_SMALL_NOT_FOUND));

            // interestSmall의 interestBig 값이 interestBig와 같은지 확인
            if (!interestSmall.getInterestBig().getInterestBigId().equals(interestBig.getInterestBigId())) {
                throw new CustomException(ErrorCode.INTEREST_BAD_REQUEST);
            }
        }

        // S3 이미지 업로드
        String originalImg = null;
        String saveImg = null;

        // 이미지 변경 O => 변경하는 이미지 업로드 후 DB 저장
        if (!image.isEmpty()) {
            saveImg = s3ImageService.upload(image);
            originalImg = image.getOriginalFilename();
        }
        // 이미지 변경 X => 기존 이미지 그대로 가져감
        else if (crewSaveReqDto.getOriginalImg() != null && crewSaveReqDto.getSaveImg() != null) {
            if (!crewSaveReqDto.getSaveImg().equals(crew.getSaveImg()) && !crewSaveReqDto.getOriginalImg().equals(crew.getOriginalImg())) {
                throw new CustomException(ErrorCode.IMAGE_BAD_REQUEST);
            }
            originalImg = crewSaveReqDto.getOriginalImg();
            saveImg = crewSaveReqDto.getSaveImg();
        }
        // 원본 이미지 또는 저장 이미지 하나만 널일 경우
        else if (crewSaveReqDto.getOriginalImg() != null || crewSaveReqDto.getSaveImg() != null) {
            throw new CustomException(ErrorCode.IMAGE_BAD_REQUEST);
        }
        // 이미지 삭제 => null

        // Crew 객체 업데이트
        crew.updateCrew(crewSaveReqDto.toEntity(geo, interestBig, interestSmall, originalImg, saveImg));

        // DB 수정
        Crew updateCrew = crewRepository.save(crew);

        return CrewSaveRespDto.builder().crew(updateCrew).build();
    }

    // 모임 삭제
    public void deleteCrew(Long crewId) {
        // 현재 로그인 한 사용자 정보 가져오기
        Long memberId = 101L;   // 테스트용
        Member member = memberRepository.findByMemberIdAndStatus(memberId, 1) // 나중에 상태값까지 비교
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        // 해당 모임이 존재하는지 검증
        Crew crew = crewRepository.findByCrewIdAndStatus(crewId, 1)
                .orElseThrow(() -> new CustomException(ErrorCode.CREW_NOT_FOUND));

        // 해당 모임의 모임장인지 확인
        if (!crewMemberRepository.existsByCrewAndMemberAndRole(crew, member, CrewMemberRole.LEADER)) {
            throw new CustomException(ErrorCode.CREW_ACCESS_DENIED);
        }

        // 삭제할 모임 상태값 변경
        crew.changeStatus(0);
        // DB 수정
        crewRepository.save(crew);
    }

    // 특정 모임 조회
    public CrewSelectRespDto getCrewByCrewId(Long crewId) {
        Crew crew = crewRepository.findByCrewIdAndStatus(crewId, 1)
                .orElseThrow(() -> new CustomException(ErrorCode.CREW_NOT_FOUND));
        return CrewSelectRespDto.builder().crew(crew).build();
    }

    // 모임 가입 신청
    @Override
    public CrewMemberSaveRespDto signUpCrew(Long crewId) {
        // 현재 로그인 한 사용자 정보 가져오기
        Long memberId = 101L;   // 테스트용
        Member member = memberRepository.findById(memberId) // 나중에 상태값까지 비교
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        // 해당 모임이 존재하는지 검증
        Crew crew = crewRepository.findByCrewIdAndStatus(crewId, 1)
                .orElseThrow(() -> new CustomException(ErrorCode.CREW_NOT_FOUND));


        // 모임원으로 존재하는지 검증
        CrewMember crewMember = crewMemberRepository.findByCrewAndMember(crew, member);
        if (crewMember != null) {
            if (crewMember.getRole() == CrewMemberRole.MEMBER || crewMember.getRole() == CrewMemberRole.ADMIN || crewMember.getRole() == CrewMemberRole.LEADER) {
                throw new CustomException(ErrorCode.ALREADY_CREW_MEMBER);
            } else if (crewMember.getRole() == CrewMemberRole.EXPELLED) {
                throw new CustomException(ErrorCode.CREW_ACCESS_DENIED);
            } else if (crewMember.getRole() == CrewMemberRole.PENDING) {
                throw new CustomException(ErrorCode.ALREADY_PENDING);
            } else if (crewMember.getRole() == CrewMemberRole.DEPARTED) {
                crewMember.updateRole(CrewMemberRole.PENDING);
                return CrewMemberSaveRespDto.builder().crewMember(crewMemberRepository.save(crewMember)).build();
            }
        }

        // 모임멤버 추가
        CrewMember saveCrewMember = CrewMember.builder()
                .role(CrewMemberRole.PENDING)
                .crew(crew)
                .member(member)
                .build();

        return CrewMemberSaveRespDto.builder().crewMember(crewMemberRepository.save(saveCrewMember)).build();
    }

    // 관심사 별 모임 조회
    @Override
    public List<CrewSelectRespDto> getAllCrewByInterest(String city, Long interestBigId, Long interestSmallId, int page) {
        // 검증
        validateInputs(city, interestBigId, interestSmallId);

        // 페이지 번호 유효성 검사
        if (page < 0) {
            throw new CustomException(ErrorCode.INVALID_PAGE_NUMBER);
        }

        // 모임 리스트 조회
        Page<Crew> crews = null;

        if (city == null) {     // 비회원
            crews = interestBigId != null
                    ? crewRepository.findAllByInterestBig_InterestBigIdAndStatus(interestBigId, 1, PageRequest.of(page, 5, Sort.by(Sort.Direction.DESC, "totalMember")))
                    : crewRepository.findAllByInterestSmall_InterestSmallIdAndStatus(interestSmallId, 1, PageRequest.of(page, 5, Sort.by(Sort.Direction.DESC, "totalMember")));
        } else {                // 회원
            crews = interestBigId != null
                    ? crewRepository.findAllByGeo_CityAndInterestBig_InterestBigIdAndStatus(city, interestBigId, 1, PageRequest.of(page, 5, Sort.by(Sort.Direction.DESC, "totalMember")))
                    : crewRepository.findAllByGeo_CityAndInterestSmall_InterestSmallIdAndStatus(city, interestSmallId, 1, PageRequest.of(page, 5, Sort.by(Sort.Direction.DESC, "totalMember")));
        }

        return crews.stream()
                .map(CrewSelectRespDto::new)
                .toList();
    }

    // 조회 유효성 검사
    private void validateInputs(String city, Long interestBigId, Long interestSmallId) {
        if (city != null && !geoRepository.existsByCity(city)) {
            throw new CustomException(ErrorCode.GEO_NOT_FOUND);
        }
        if (interestBigId != null && !interestBigRepository.existsById(interestBigId)) {
            throw new CustomException(ErrorCode.INTEREST_BIG_NOT_FOUND);
        }
        if (interestSmallId != null && !interestSmallRepository.existsById(interestSmallId)) {
            throw new CustomException(ErrorCode.INTEREST_SMALL_NOT_FOUND);
        }
        if ((interestBigId == null && interestSmallId == null) || (interestBigId != null && interestSmallId != null)) {
            throw new CustomException(ErrorCode.CREW_BAD_REQUEST);
        }
    }

    // 특정 모임의 모임원 조회
    public List<CrewMemberSelectRespDto> getCrewMemberByCrewId(Long crewId) {
        // 유효한 모임인지 검증
        if (!crewRepository.existsByCrewIdAndStatus(crewId, 1)) {
            throw new CustomException(ErrorCode.CREW_NOT_FOUND);
        }

        List<CrewMemberRole> roles = Arrays.asList(
                CrewMemberRole.MEMBER,
                CrewMemberRole.ADMIN,
                CrewMemberRole.LEADER
        );

        List<CrewMember> crewMembers = crewMemberRepository.findByCrew_CrewIdAndRoleInOrderByRoleDesc(crewId, roles);

        return crewMembers.stream()
                .map(CrewMemberSelectRespDto::new)
                .toList();
    }

    // 특정 모임의 가입신청 조회
    public List<CrewMemberSelectRespDto> getSignUpMemberByCrewId(Long crewId) {
        // 현재 로그인 한 사용자 정보 가져오기
        Long memberId = 101L;   // 테스트용
        Member member = memberRepository.findByMemberIdAndStatus(memberId, 1) // 나중에 상태값까지 비교
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        // 해당 모임이 존재하는지 검증
        Crew crew = crewRepository.findByCrewIdAndStatus(crewId, 1)
                .orElseThrow(() -> new CustomException(ErrorCode.CREW_NOT_FOUND));

        // 해당 모임의 멤버인지 확인
        if (!crewMemberRepository.existsByCrewAndMemberAndRole(crew, member, CrewMemberRole.LEADER)
            && !crewMemberRepository.existsByCrewAndMemberAndRole(crew, member, CrewMemberRole.ADMIN)) {
            throw new CustomException(ErrorCode.CREW_ACCESS_DENIED);
        }

        List<CrewMember> crewMembers = crewMemberRepository.findByCrew_CrewIdAndRole(crewId, CrewMemberRole.PENDING);

        return crewMembers.stream()
                .map(CrewMemberSelectRespDto::new)
                .toList();
    }

    // 모임 좋아요
    public CrewLikeSaveRespDto likeCrew(Long crewId) {
        // 유효한 모임인지 검증
        Crew crew = crewRepository.findByCrewIdAndStatus(crewId, 1)
                .orElseThrow(() -> new CustomException(ErrorCode.CREW_NOT_FOUND));

        // 현재 로그인 한 사용자 정보 가져오기
        Long memberId = 101L;   // 테스트용
        Member member = memberRepository.findByMemberIdAndStatus(memberId, 1) // 나중에 상태값까지 비교
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        // 좋아요를 했는지 확인
        CrewLike crewLike = crewLikeRepository.findByCrew_CrewIdAndMember_MemberId(crewId, memberId);

        CrewLike saveCrewLike = null;

        if (crewLike != null) {
            throw new CustomException(ErrorCode.ALREADY_CREW_LIKE);
        }

        // 총 좋아요 수 +1
        crew.changeTotalLike(1);
        crewRepository.save(crew);

        crewLike = CrewLike.builder()
                .crew(crew)
                .member(member)
                .build();

        return CrewLikeSaveRespDto.builder().crewLike(crewLikeRepository.save(crewLike)).build();
    }

    // 모임 좋아요 취소
    public void deleteLikeCrew(Long crewId) {
        // 유효한 모임인지 검증
        Crew crew = crewRepository.findByCrewIdAndStatus(crewId, 1)
                .orElseThrow(() -> new CustomException(ErrorCode.CREW_NOT_FOUND));

        // 현재 로그인 한 사용자 정보 가져오기
        Long memberId = 101L;   // 테스트용
        Member member = memberRepository.findByMemberIdAndStatus(memberId, 1) // 나중에 상태값까지 비교
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        // 좋아요를 했는지 확인
        CrewLike crewLike = crewLikeRepository.findByCrew_CrewIdAndMember_MemberId(crewId, memberId);

        if (crewLike == null) {
            throw new CustomException(ErrorCode.NOT_CREW_LIKE);
        }

        // 총 좋아요 수 -1
        crew.changeTotalLike(-1);
        crewRepository.save(crew);

        crewLikeRepository.delete(crewLike);
    }

    // 모임 찜 여부 조회
    public boolean isLikeCrew(Long crewId) {
        // 유효한 모임인지 검증
        Crew crew = crewRepository.findByCrewIdAndStatus(crewId, 1)
                .orElseThrow(() -> new CustomException(ErrorCode.CREW_NOT_FOUND));

        // 현재 로그인 한 사용자 정보 가져오기
        Long memberId = 101L;   // 테스트용
        Member member = memberRepository.findByMemberIdAndStatus(memberId, 1) // 나중에 상태값까지 비교
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        return crewLikeRepository.existsByCrewAndMember(crew, member);
    }

    // 권한 검증
    private void validateRole() {
        // 추후 업데이트
        throw new CustomException(ErrorCode.CREW_ACCESS_DENIED);
    }

    public CrewMemberSaveRespDto updateRole(Long crewId, CrewMemberSaveReqDto crewMemberSaveReqDto) {
        // 유효한 모임인지 검증
        Crew crew = crewRepository.findByCrewIdAndStatus(crewId, 1)
                .orElseThrow(() -> new CustomException(ErrorCode.CREW_NOT_FOUND));

        // 현재 로그인 한 사용자 정보 가져오기
        Long memberId = 101L;   // 테스트용
        Member member = memberRepository.findByMemberIdAndStatus(memberId, 1) // 나중에 상태값까지 비교
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        // 변경자의 정보 가져오기
        CrewMember initiator = crewMemberRepository.findByCrew_CrewIdAndMember_MemberId(crewId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.CREW_MEMBER_NOT_FOUND));

        // 변경 대상의 정보 가져오기
        CrewMember target = crewMemberRepository.findByCrew_CrewIdAndMember_MemberId(crewId, crewMemberSaveReqDto.getMemberId())
                .orElseThrow(() -> new CustomException(ErrorCode.CREW_MEMBER_NOT_FOUND));

        // 변경할 역할
        CrewMemberRole newRole = CrewMemberRole.enumOf(crewMemberSaveReqDto.getNewRoleStatus());

        // role 변경 가능한지 확인
        canChangeRole(initiator.getRole(), target.getRole(), newRole);

        // 모임장을 임명하는 경우 , 로그인 유저는 운영진으로 변경
        if (newRole == CrewMemberRole.LEADER) {
            initiator.updateRole(CrewMemberRole.ADMIN);
            crewMemberRepository.save(initiator);
        }
        // 가입 신청 승인시 총 모임원 수 +1
        if (target.getRole() == CrewMemberRole.PENDING && newRole == CrewMemberRole.MEMBER) {
            crew.changeTotalMember(1);
            crewRepository.save(crew);
        }
        // 회원 강퇴 또는 퇴장 시 총 모임원 수 -1
        if ((target.getRole() == CrewMemberRole.MEMBER && newRole == CrewMemberRole.EXPELLED) ||
            (target.getRole() == CrewMemberRole.MEMBER && newRole == CrewMemberRole.DEPARTED) ||
            (target.getRole() == CrewMemberRole.ADMIN && newRole == CrewMemberRole.DEPARTED)) {
            crew.changeTotalMember(-1);
            crewRepository.save(crew);
        }

        // 권한 변경
        target.updateRole(newRole);
        CrewMember crewMember = crewMemberRepository.save(target);

        return CrewMemberSaveRespDto.builder().crewMember(crewMember).build();
    }

    // role 변경 권한 검사
    private void canChangeRole(CrewMemberRole initiatorRole, CrewMemberRole targetRole, CrewMemberRole newRole) {
        if (initiatorRole == CrewMemberRole.EXPELLED || initiatorRole == CrewMemberRole.MEMBER ||
            // 모임장 , 운영진 제외 접근 불가
            initiatorRole == CrewMemberRole.PENDING || initiatorRole == CrewMemberRole.DEPARTED) {
            throw new CustomException(ErrorCode.CREW_ACCESS_DENIED);
        } else if (initiatorRole == CrewMemberRole.ADMIN && newRole == CrewMemberRole.LEADER) {
            // 운영진은 모임장 위임 불가
            throw new CustomException(ErrorCode.CREW_ACCESS_DENIED);
        } else if (targetRole == CrewMemberRole.EXPELLED || targetRole == CrewMemberRole.LEADER || targetRole == CrewMemberRole.DEPARTED) {
            // 강퇴 멤버 , 모임장 , 퇴장 멤버의 role 변경 불가
            throw new CustomException(ErrorCode.CREW_ACCESS_DENIED);
        } else if (targetRole == CrewMemberRole.MEMBER && (newRole == CrewMemberRole.MEMBER || newRole == CrewMemberRole.PENDING || newRole == CrewMemberRole.DEPARTED)) {
            // 일반 멤버는 운영진 || 모임장 || 퇴장만 변경 가능
            throw new CustomException(ErrorCode.CREW_ACCESS_DENIED);
        } else if (targetRole == CrewMemberRole.ADMIN && (newRole != CrewMemberRole.LEADER && newRole != CrewMemberRole.MEMBER && newRole != CrewMemberRole.DEPARTED)) {
            // 운영진은 일반 멤버 || 모임장 || 퇴장만 변경 가능
            throw new CustomException(ErrorCode.CREW_ACCESS_DENIED);
        } else if (targetRole == CrewMemberRole.PENDING && (newRole != CrewMemberRole.EXPELLED && newRole != CrewMemberRole.MEMBER && newRole != CrewMemberRole.DEPARTED)) {
            // 승인 대기 멤버는 강퇴 || 일반 멤버로만 변경 가능
            throw new CustomException(ErrorCode.CREW_ACCESS_DENIED);
        }
    }

}
