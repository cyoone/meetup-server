package site.mymeetup.meetupserver.crew.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import site.mymeetup.meetupserver.crew.dto.CrewDto;
import site.mymeetup.meetupserver.crew.service.CrewService;
import site.mymeetup.meetupserver.response.ApiResponse;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CrewController {
    private final CrewService crewService;

    // 모임 등록
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/crews")
    public ApiResponse<?> createCrew(@RequestPart MultipartFile image,
                                     @RequestPart @Valid CrewDto.CrewSaveReqDto crewSaveReqDto) {
        CrewDto.CrewSaveRespDto crewSaveRespDto = crewService.createCrew(crewSaveReqDto, image);
        return ApiResponse.success(crewSaveRespDto);
    }

    // 모임 수정
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/crews/{crewId}")
    public ApiResponse<?> updateCrew(@PathVariable("crewId") Long crewId,
                                     @RequestPart MultipartFile image,
                                     @RequestPart @Valid CrewDto.CrewSaveReqDto crewSaveReqDto) {
        CrewDto.CrewSaveRespDto crewSaveRespDto = crewService.updateCrew(crewId, crewSaveReqDto, image);
        return ApiResponse.success(crewSaveRespDto);
    }
}
