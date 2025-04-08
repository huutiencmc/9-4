package com.example.hrms.biz.booking.model.dto;

import com.example.hrms.biz.booking.model.Booking;
import com.example.hrms.enumation.BookingType;
import com.example.hrms.enumation.BookingStatusEnum;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.BeanUtils;
import com.example.hrms.utils.DateUtils;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

public class BookingDTO {

    @Getter
    @Setter
    public static class Req {
        private Long bookingId;
        private String username;
        private Long roomId;
        private String title;
        private String attendees;
        private String content;
        private String startDate;
        private String endDate;
        private String startTime;
        private String endTime;
        private String status;

        @NotBlank(message = "Booking type is mandatory")
        private String bookingType;

        private List<String> weekdays;

        public static Req fromBooking(Booking booking) {
            Req req = new Req();
            BeanUtils.copyProperties(booking, req);
            DateTimeFormatter formatter = DateUtils.getDateTimeFormatter();
            req.setStartDate(booking.getStartTime() != null ? booking.getStartTime().format(DateTimeFormatter.ISO_LOCAL_DATE) : null);
            req.setStartTime(booking.getStartTime() != null ? booking.getStartTime().format(formatter) : null);
            req.setEndDate(booking.getEndTime() != null ? booking.getEndTime().format(DateTimeFormatter.ISO_LOCAL_DATE) : null);
            req.setEndTime(booking.getEndTime() != null ? booking.getEndTime().format(formatter) : null);
            req.setStatus(booking.getStatus().name());
            req.setTitle(booking.getTitle());
            req.setAttendees(booking.getAttendees());
            req.setContent(booking.getContent());
            req.setBookingType(booking.getBookingType() != null ? booking.getBookingType().name() : null);
            req.setWeekdays(Collections.singletonList(booking.getWeekdays()));
            return req;
        }

        public Booking toBooking() {
            Booking booking = new Booking();
            BeanUtils.copyProperties(this, booking);
            DateTimeFormatter formatter = DateUtils.getDateTimeFormatter();
            booking.setStartTime(this.startDate != null && this.startTime != null
                    ? LocalDateTime.parse(this.startDate + " " + this.startTime, formatter)
                    : null);
            booking.setEndTime(this.endDate != null && this.endTime != null
                    ? LocalDateTime.parse(this.endDate + " " + this.endTime, formatter)
                    : null);
            booking.setStatus(this.status != null ? BookingStatusEnum.valueOf(this.status) : BookingStatusEnum.Requested);
            booking.setBookingType(this.bookingType != null ? BookingType.valueOf(this.bookingType) : BookingType.ONLY);
            booking.setWeekdays(this.weekdays != null ? String.join(",", this.weekdays) : null); // Comma-separated string
            booking.setUsername(this.username != null ? this.username : "defaultUsername");
            booking.setRoomId(this.roomId != null ? this.roomId : 1L);
            booking.setContent(this.content);
            return booking;
        }
    }

    @Getter
    @Setter
    public static class Resp {
        private Long bookingId;
        private String username;
        private Long roomId;
        private String title;
        private String attendees;
        private String content;
        private String startTime;
        private String endTime;
        private String status;
        private String bookingType;
        private String weekdays;

        public static Resp toResponse(Booking booking) {
            Resp resp = new Resp();
            BeanUtils.copyProperties(booking, resp);
            DateTimeFormatter formatter = DateUtils.getDateTimeFormatter();
            resp.setStartTime(booking.getStartTime() != null ? booking.getStartTime().format(formatter) : null);
            resp.setEndTime(booking.getEndTime() != null ? booking.getEndTime().format(formatter) : null);
            resp.setStatus(booking.getStatus().name());
            resp.setTitle(booking.getTitle());
            resp.setAttendees(booking.getAttendees());
            resp.setContent(booking.getContent());
            resp.setBookingType(booking.getBookingType() != null ? booking.getBookingType().name() : null);
            resp.setWeekdays(booking.getWeekdays());
            return resp;
        }
    }
}