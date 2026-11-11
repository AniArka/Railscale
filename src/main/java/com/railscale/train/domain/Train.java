package com.railscale.train.domain;

import com.railscale.common.model.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trains")
public class Train extends BaseEntity {

    @Column(name = "train_number", nullable = false, unique = true, length = 20)
    private String trainNumber;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "total_coaches", nullable = false)
    private int totalCoaches = 0;

    @OneToMany(mappedBy = "train", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Coach> coaches = new ArrayList<>();

    public Train() {
    }

    public Train(String trainNumber, String name) {
        this.trainNumber = trainNumber;
        this.name = name;
    }

    public void addCoach(Coach coach) {
        coaches.add(coach);
        coach.setTrain(this);
        this.totalCoaches = coaches.size();
    }

    public String getTrainNumber() {
        return trainNumber;
    }

    public void setTrainNumber(String trainNumber) {
        this.trainNumber = trainNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getTotalCoaches() {
        return totalCoaches;
    }

    public void setTotalCoaches(int totalCoaches) {
        this.totalCoaches = totalCoaches;
    }

    public List<Coach> getCoaches() {
        return coaches;
    }

    public void setCoaches(List<Coach> coaches) {
        this.coaches = coaches;
        this.totalCoaches = coaches != null ? coaches.size() : 0;
    }
}
