package com.example.hrms.biz.booking.service;

import com.example.hrms.biz.booking.model.Booking;
import com.example.hrms.biz.booking.model.criteria.BookingCriteria;
import com.example.hrms.biz.booking.model.dto.BookingDTO;
import com.example.hrms.biz.booking.repository.BookingMapper;
import com.example.hrms.utils.DateUtils;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

import com.example.hrms.enumation.BookingType;

@Service
public class BookingService {
    private final BookingMapper bookingMapper;

    public BookingService(BookingMapper bookingMapper) {
        this.bookingMapper = bookingMapper;
    }

    public Booking getBookingById(Long bookingId) {
        return bookingMapper.selectById(bookingId);
    }

    public void insert(BookingDTO.Req req) {
        List<Booking> booking = handleBookingType(req);

        if (!booking.isEmpty()) {
            bookingMapper.insert(req.toBooking());
        }
    }

    public void updateBooking(Booking req) {
        // Ensure date and time fields are set
        if (req.getStartTime() == null || req.getEndTime() == null) {
            throw new IllegalArgumentException("Date and time fields cannot be null or empty");
        }

        List<Booking> booking = handleBookingType(BookingDTO.Req.fromBooking(req));
        if (!booking.isEmpty()) {
            bookingMapper.updateBooking(req);
        }
    }


    public void deleteBooking(Long bookingId) {
        bookingMapper.deleteBooking(bookingId);
    }

    public boolean isConflict(Booking booking) {
        List<Booking> conflictingBookings = bookingMapper.findConflictingBookings(
                booking.getRoomId(), booking.getStartTime(), booking.getEndTime()
        );
        return !conflictingBookings.isEmpty();
    }

    public int count(BookingCriteria criteria) {
        return bookingMapper.count(criteria);
    }

    public List<BookingDTO.Resp> list(BookingCriteria criteria) {
        List<Booking> bookings = bookingMapper.select(criteria);
        return bookings.stream().map(BookingDTO.Resp::toResponse).toList();
    }

    public List<BookingDTO.Resp> getAllBookings() {
        List<Booking> bookings = bookingMapper.selectAll();
        return bookings.stream().map(BookingDTO.Resp::toResponse).toList();
    }

    private List<Booking> handleBookingType(BookingDTO.Req req) {
        List<Booking> bookings = new ArrayList<>();


        // Log the input data
        System.out.println("Start Date: " + req.getStartDate());
        System.out.println("Start Time: " + req.getStartTime());
        System.out.println("End Date: " + req.getEndDate());
        System.out.println("End Time: " + req.getEndTime());

        // Kiểm tra dữ liệu đầu vào
        if (req.getStartDate() == null || req.getStartTime() == null ||
                req.getEndDate() == null || req.getEndTime() == null ||
                req.getStartDate().trim().isEmpty() || req.getStartTime().trim().isEmpty() ||
                req.getEndDate().trim().isEmpty() || req.getEndTime().trim().isEmpty()) {
            throw new IllegalArgumentException("Date and time fields cannot be null or empty");
        }

        try {
            LocalDateTime startDateTime = DateUtils.parseDateTime(req.getStartDate() + " " + req.getStartTime());
            LocalDateTime endDateTime = DateUtils.parseDateTime(req.getEndDate() + " " + req.getEndTime());

            switch (req.getBookingType().toUpperCase()) {
                case "ONLY":
                    Booking booking = req.toBooking();
                    booking.setBookingType(BookingType.ONLY);
                    LocalDateTime today = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                            .withHour(startDateTime.getHour())
                            .withMinute(startDateTime.getMinute())
                            .withSecond(startDateTime.getSecond())
                            .withNano(startDateTime.getNano());
                    booking.setStartTime(LocalDateTime.parse(DateUtils.formatDateTime(today)));
                    booking.setEndTime(LocalDateTime.parse(DateUtils.formatDateTime(today)));
                    booking.setWeekdays(null);
                    bookings.add(booking);
                    break;

                case "DAILY":
                    long daysBetween = ChronoUnit.DAYS.between(startDateTime.toLocalDate(), endDateTime.toLocalDate()) + 1;
                    for (int i = 0; i < daysBetween; i++) {
                        LocalDateTime startTimeToday = startDateTime.plusDays(i);
                        LocalDateTime endTimeToday = startTimeToday.withHour(endDateTime.getHour())
                                .withMinute(endDateTime.getMinute())
                                .withSecond(endDateTime.getSecond())
                                .withNano(endDateTime.getNano());
                        Booking dailyBooking = req.toBooking();
                        dailyBooking.setBookingType(BookingType.DAILY);
                        dailyBooking.setStartTime(LocalDateTime.parse(DateUtils.formatDateTime(startTimeToday)));
                        dailyBooking.setEndTime(LocalDateTime.parse(DateUtils.formatDateTime(endTimeToday)));
                        dailyBooking.setWeekdays(null);
                        bookings.add(dailyBooking);
                    }
                    break;

                case "WEEKLY":
                    List<String> weekdays = (req.getWeekdays() != null && !req.getWeekdays().isEmpty())
                            ? req.getWeekdays()
                            : Arrays.asList("mo", "tu", "we", "th", "fr");

                    LocalDateTime current = startDateTime;
                    while (!current.isAfter(endDateTime)) {
                        for (String day : weekdays) {
                            LocalDateTime startTimeToday = getDayOfWeek(current, day);
                            if (startTimeToday != null && !startTimeToday.isBefore(startDateTime) && !startTimeToday.isAfter(endDateTime)) {
                                LocalDateTime endTimeToday = startTimeToday.withHour(endDateTime.getHour())
                                        .withMinute(endDateTime.getMinute())
                                        .withSecond(endDateTime.getSecond())
                                        .withNano(endDateTime.getNano());
                                Booking weeklyBooking = req.toBooking();
                                weeklyBooking.setBookingType(BookingType.WEEKLY);
                                weeklyBooking.setStartTime(LocalDateTime.parse(DateUtils.formatDateTime(startTimeToday)));
                                weeklyBooking.setEndTime(LocalDateTime.parse(DateUtils.formatDateTime(endTimeToday)));
                                weeklyBooking.setWeekdays(String.valueOf(new ArrayList<>(Collections.singletonList(day)))); // Đổi sang List<String>
                                bookings.add(weeklyBooking);
                            }
                        }
                        current = current.plusWeeks(1);
                    }
                    break;

                default:
                    throw new IllegalArgumentException("Invalid booking type: " + req.getBookingType());
            }
        } catch (DateTimeParseException e) {
            System.err.println("Failed to parse date/time: " + e.getMessage());
            throw new IllegalArgumentException("Invalid date/time format: " + e.getMessage(), e);
        }
        return bookings;
    }

    // Cải tiến hàm lấy ngày trong tuần
    private LocalDateTime getDayOfWeek(LocalDateTime start, String day) {
        Map<String, DayOfWeek> dayMap = Map.of(
                "mo", DayOfWeek.MONDAY, "tu", DayOfWeek.TUESDAY, "we", DayOfWeek.WEDNESDAY,
                "th", DayOfWeek.THURSDAY, "fr", DayOfWeek.FRIDAY
        );
        DayOfWeek targetDay = dayMap.get(day.toLowerCase());
        if (targetDay == null) return null;

        LocalDateTime targetDate = start.with(TemporalAdjusters.nextOrSame(targetDay));
        return targetDate.isAfter(start) ? targetDate : targetDate.plusWeeks(1);
    }
}
